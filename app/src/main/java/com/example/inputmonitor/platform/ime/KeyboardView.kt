package com.example.inputmonitor.platform.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioManager
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout

/**
 * High-performance, low-latency native Android view for the custom keyboard (DEVELOPMENT_GUIDELINES.md §4).
 *
 * Includes an offline emoji picker using the device's emoji font.
 */
class KeyboardView(
    context: Context,
    private val onKeyAction: (KeyAction) -> Unit
) : LinearLayout(context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var currentLayout: KeyboardLayout? = null
    private var showingEmoji = false
    private var suggestions = emptyList<String>()
    var onSuggestion: (String) -> Unit = {}
    var onMedia: (String) -> Unit = {}
    private var suggestionStrip: LinearLayout? = null
    private var toolbarActions: LinearLayout? = null
    private var suggestionsAllowed = false

    fun setSuggestionsAllowed(allowed: Boolean) {
        suggestionsAllowed = allowed
        setSuggestions(if (allowed) suggestions else emptyList())
    }

    fun setSuggestions(values: List<String>) {
        suggestions = values
        suggestionStrip?.let { strip ->
            strip.removeAllViews()
            values.forEach { value ->
                strip.addView(createPickerButton(value, "Use suggestion $value") { onSuggestion(value) },
                    LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            }
            val showSuggestions = suggestionsAllowed && values.isNotEmpty()
            strip.visibility = if (showSuggestions) View.VISIBLE else View.GONE
            toolbarActions?.visibility = if (showSuggestions) View.GONE else View.VISIBLE
        }
    }
    private var dark = KeyboardAppearance.isDark(context)
    private val textColor get() = Color.parseColor(if (dark) "#E3E3E3" else "#303134")
    private val surfaceColor get() = Color.parseColor(if (dark) "#1E1F20" else "#ECEEF1")
    private val accentColor get() = Color.parseColor(if (dark) "#A8C7FA" else "#C2D7FC")

    init {
        orientation = VERTICAL
        setBackgroundColor(surfaceColor)
        isFocusable = false
        isFocusableInTouchMode = false
        val pad = dpToPx(4)
        setPadding(pad, pad, pad, pad)
        setOnApplyWindowInsetsListener { _, insets ->
            val bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            setPadding(pad, pad, pad, pad + bottom)
            insets
        }
    }

    /**
     * Binds and renders the given [KeyboardLayout].
     */
    fun setLayout(layout: KeyboardLayout) {
        dark = KeyboardAppearance.isDark(context)
        setBackgroundColor(surfaceColor)
        currentLayout = layout
        removeAllViews()
        suggestionStrip = null

        if (showingEmoji && layout.type != KeyboardLayoutType.NUMERIC && layout.type != KeyboardLayoutType.PHONE) {
            renderEmojiPicker()
            return
        }
        showingEmoji = false
        renderToolbar()
        setSuggestions(suggestions)
        for ((index, row) in layout.rows.withIndex()) {
            val rowView = createRowView(row, bottomRow = index == layout.rows.lastIndex)
            if (index == 1 && layout.type in listOf(KeyboardLayoutType.QWERTY, KeyboardLayoutType.EMAIL, KeyboardLayoutType.URI)) {
                // Half-key spacers keep the home row aligned at any window width.
                rowView.addView(View(context), 0, LayoutParams(0, LayoutParams.MATCH_PARENT, 0.5f))
                rowView.addView(View(context), LayoutParams(0, LayoutParams.MATCH_PARENT, 0.5f))
            }
            addView(rowView)
        }
    }

    /** A newly shown editor always starts with letters/numbers, not a stale emoji panel. */
    fun startInput(layout: KeyboardLayout) {
        showingEmoji = false
        visibility = View.VISIBLE
        setLayout(layout)
        requestLayout()
    }

    private fun createPickerButton(label: String, description: String, action: () -> Unit): Button {
        val icon = when (description) {
            "Search emoji", "Open emoji picker" -> KeyboardIcon.SMILE
            "Choose a sticker image" -> KeyboardIcon.STICKER
            "Switch light or dark theme. Long press to follow system" -> KeyboardIcon.PALETTE
            else -> null
        }
        val iconPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = textColor }
        return object : Button(context) {
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                icon?.draw(canvas, iconPaint, width, height, dpToPx(22).toFloat())
            }
        }.apply {
            text = if (icon == null) label else ""
            contentDescription = description
            isAllCaps = false
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            stateListAnimator = null
            isFocusable = false
            setPadding(0, 0, 0, 0)
            setTextColor(textColor)
            includeFontPadding = false
            backgroundTintList = null
            background = createToolbarBackground()
            setOnClickListener { action() }
        }
    }

    private fun renderToolbar() {
        val toolbar = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4), 0, dpToPx(4), 0)
        }
        toolbar.addView(android.widget.TextView(context).apply {
            text = "Input Monitor"
            textSize = 11f
            setTextColor(textColor)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            contentDescription = "Input Monitor keyboard"
        }, LayoutParams(dpToPx(92), LayoutParams.MATCH_PARENT))
        val actions = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbarActions = actions
        actions.addView(createPickerButton("☺\uFE0E", "Search emoji") {
            showingEmoji = true; currentLayout?.let(::setLayout)
        }.apply { textSize = 23f }, LayoutParams(0, dpToPx(36), 1f))
        actions.addView(createPickerButton("GIF", "Choose a GIF file") { onMedia("image/gif") }
            .apply { textSize = 13f }, LayoutParams(0, dpToPx(36), 1f))
        actions.addView(createPickerButton("▧", "Choose a sticker image") { onMedia("image/*") }
            .apply { textSize = 23f }, LayoutParams(0, dpToPx(36), 1f))
        actions.addView(createPickerButton(if (dark) "☀\uFE0E" else "☾", "Switch light or dark theme. Long press to follow system") {
            KeyboardAppearance.toggle(context)
            currentLayout?.let(::setLayout)
        }.apply {
            textSize = 21f
            setOnLongClickListener {
                KeyboardAppearance.followSystem(context)
                currentLayout?.let(::setLayout)
                true
            }
        }, LayoutParams(0, dpToPx(36), 1f))
        toolbar.addView(actions, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        suggestionStrip = LinearLayout(context).apply {
            orientation = HORIZONTAL
            visibility = View.GONE
        }.also { toolbar.addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
        addView(toolbar, LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(42)))
    }

    private fun createToolbarBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply {
            setColor(if (dark) Color.parseColor("#5F6368") else Color.parseColor("#DADCE0"))
            cornerRadius = dpToPx(20).toFloat()
        })
        addState(intArrayOf(), android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
    }

    private fun renderEmojiPicker() {
        addView(EmojiPickerView(context, onKeyAction) {
            showingEmoji = false
            currentLayout?.let(::setLayout)
        }, LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(280)))
    }
    private fun createRowView(row: KeyboardRow, bottomRow: Boolean = false): LinearLayout {
        val rowLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(44)).apply {
                topMargin = dpToPx(3)
                bottomMargin = dpToPx(3)
            }
        }

        for (key in row.keys) {
            // Use the bottom-row switcher position for emoji; long-press Space still switches IMEs.
            val keyView = if (bottomRow && key.action == KeyAction.SwitchIme) {
                createEmojiButton(key.weight)
            } else createKeyView(key)
            if (bottomRow && key.action == KeyAction.Space && row.keys.none { it.action == KeyAction.SwitchIme }
                && currentLayout?.type != KeyboardLayoutType.NUMERIC && currentLayout?.type != KeyboardLayoutType.PHONE) {
                rowLayout.addView(createEmojiButton(1f))
            }
            rowLayout.addView(keyView)
        }

        return rowLayout
    }

    private fun createEmojiButton(weight: Float): View = createPickerButton("☺\uFE0E", "Open emoji picker") {
        showingEmoji = true
        currentLayout?.let(::setLayout)
    }.apply {
        textSize = 24f
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
            leftMargin = dpToPx(2)
            rightMargin = dpToPx(2)
        }
    }

    private fun createKeyView(key: KeyModel): View {
        val hintIndex = "qwertyuiop".indexOf(key.label.lowercase()).takeIf { key.label.length == 1 && it >= 0 }
        val hint = hintIndex?.let { ((it + 1) % 10).toString() }
        val hintPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) Color.parseColor("#BDC1C6") else Color.parseColor("#70757A")
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val icon = when (key.action) {
            KeyAction.Shift -> if (key.label == "⇪") KeyboardIcon.CAPS else KeyboardIcon.SHIFT
            KeyAction.Backspace -> KeyboardIcon.DELETE
            KeyAction.Enter -> if (key.label == "↵") KeyboardIcon.RETURN else null
            else -> null
        }
        val iconPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (key.action == KeyAction.Enter) Color.parseColor("#202124") else textColor
        }
        val button = object : Button(context) {
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                icon?.draw(canvas, iconPaint, width, height, dpToPx(24).toFloat())
                if (hint != null) canvas.drawText(hint, width - dpToPx(4).toFloat(),
                    dpToPx(2).toFloat() - hintPaint.fontMetrics.top, hintPaint)
            }
        }.apply {
            text = if (key.action == KeyAction.Space || icon != null) "" else key.label
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            contentDescription = when (key.action) {
                KeyAction.Space -> "Space. Long press to change keyboard"
                KeyAction.Backspace -> "Delete"
                KeyAction.Shift -> "Shift"
                else -> key.label
            } + if (hint != null) ". Long press for $hint" else ""
            textSize = if (key.action == KeyAction.Space) 14f else if (key.label.length > 2) 14f else 23f
            setTextColor(if (key.action == KeyAction.Enter) Color.parseColor("#202124") else textColor)
            isAllCaps = false
            isFocusable = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            stateListAnimator = null
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)

            includeFontPadding = false
            backgroundTintList = null
            background = createKeyBackground(key.isSpecial, key.action == KeyAction.Enter,
                key.action == KeyAction.Enter || key.action is KeyAction.SwitchLayout)

            val params = LayoutParams(0, LayoutParams.MATCH_PARENT, key.weight).apply {
                leftMargin = dpToPx(2)
                rightMargin = dpToPx(2)
            }
            layoutParams = params

            // Dispatch after a completed tap: rebuilding a layout on ACTION_DOWN can
            // detach the pressed view, and scrolling the emoji grid must not type.
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                playKeySound()
                onKeyAction(key.action)
            }
            if (key.action == KeyAction.Space) {
                setOnLongClickListener {
                    onKeyAction(KeyAction.SwitchIme)
                    true
                }
            } else if (key.action is KeyAction.Character) {
                val alternatives = when (key.label.lowercase()) {
                    "q" -> "1"; "w" -> "2"; "e" -> "3 è é ê ë ē"
                    "r" -> "4"; "t" -> "5"; "y" -> "6 ÿ ý"
                    "u" -> "7 ù ú û ü ū"; "i" -> "8 ì í î ï ī"
                    "o" -> "9 ò ó ô ö õ ø œ ō"; "p" -> "0"
                    "a" -> "à á â ä æ ã å ā"; "s" -> "ß ś š"
                    "d" -> "ď đ"; "l" -> "ł"; "z" -> "ž ź ż"
                    "c" -> "ç ć č"; "n" -> "ñ ń"; "." -> ", ? ! : ; …"
                    "$" -> "¢ £ € ¥ ₹"; "-" -> "– — _"; "0" -> "⁰ ∅"
                    else -> ""
                }.split(" ").filter { it.isNotEmpty() }
                if (alternatives.isNotEmpty()) setOnLongClickListener { anchor ->
                    val options = LinearLayout(context).apply {
                        setBackgroundColor(surfaceColor)
                        setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                    }
                    val scroll = android.widget.HorizontalScrollView(context).apply { addView(options) }
                    val popup = android.widget.PopupWindow(scroll,
                        minOf(dpToPx(42) * alternatives.size + dpToPx(8), resources.displayMetrics.widthPixels - dpToPx(16)),
                        dpToPx(52), true).apply {
                        setBackgroundDrawable(GradientDrawable().apply { setColor(surfaceColor) })
                        isOutsideTouchable = true
                        elevation = dpToPx(8).toFloat()
                    }
                    alternatives.forEach { alternate ->
                        val value = if (key.label != key.label.lowercase()) alternate.uppercase() else alternate
                        options.addView(createPickerButton(value, "Type $value") {
                            onKeyAction(KeyAction.Character(value))
                            popup.dismiss()
                        }, LayoutParams(dpToPx(42), LayoutParams.MATCH_PARENT))
                    }
                    popup.showAsDropDown(anchor, 0, -anchor.height - dpToPx(54))
                    true
                }
            }
        }

        return button
    }

    private fun playKeySound() {
        try {
            audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        } catch (_: Exception) {
            // Audio failure must never block typing
        }
    }

    private fun createKeyBackground(isSpecial: Boolean, isAction: Boolean = false, rounded: Boolean = false): StateListDrawable {
        val normalColor = when {
            isAction -> accentColor
            isSpecial -> Color.parseColor(if (dark) "#454746" else "#DADCE0")
            else -> Color.parseColor(if (dark) "#37393B" else "#FFFFFF")
        }
        val pressedColor = Color.parseColor(if (dark) "#5F6368" else "#BDC1C6")
        val cornerRadius = dpToPx(if (rounded) 24 else 5).toFloat()

        val normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(normalColor)
            this.cornerRadius = cornerRadius
        }

        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(pressedColor)
            this.cornerRadius = cornerRadius
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
