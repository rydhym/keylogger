package app.cleartrail

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Sends explicit key taps only to the current editor. No text reads, history or app-state access. */
class ClearTrailInputMethod : InputMethodService() {
    private val keys = ImeKeys()
    private var panel: LinearLayout? = null
    private var privateField = false
    private var acceptsInput = false
    private val ink = Color.rgb(27, 45, 51)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        window?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    // Never display a mirrored/fullscreen editor or suggestion/character previews.
    override fun onEvaluateFullscreenMode() = false
    override fun onCreateInputView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 248, 245))
            setPadding(dp(3), dp(4), dp(3), dp(4))
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(dp(3), dp(4), dp(3), dp(4) + insets.systemWindowInsetBottom)
                insets
            }
            panel = this
            render()
        }
    }
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val type = attribute?.inputType ?: InputType.TYPE_NULL
        acceptsInput = type != InputType.TYPE_NULL
        val kind = type and InputType.TYPE_MASK_CLASS
        val variant = type and InputType.TYPE_MASK_VARIATION
        privateField = (kind == InputType.TYPE_CLASS_NUMBER && variant == InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
            (kind == InputType.TYPE_CLASS_TEXT && variant in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        keys.reset(kind == InputType.TYPE_CLASS_NUMBER || kind == InputType.TYPE_CLASS_PHONE || kind == InputType.TYPE_CLASS_DATETIME)
        setCandidatesViewShown(false)
        render()
    }
    override fun onFinishInputView(finishingInput: Boolean) {
        keys.reset(); render()
        super.onFinishInputView(finishingInput)
    }
    override fun onFinishInput() {
        acceptsInput = false; privateField = false; keys.reset(); render()
        super.onFinishInput()
    }
    private fun render() {
        val root = panel ?: return
        root.removeAllViews()
        root.addView(TextView(this).apply {
            text = if (privateField) "ClearTrail · Private input · No history" else "ClearTrail · Typing only · No history"
            textSize = 12f; setTextColor(ink); setPadding(dp(8), dp(2), dp(8), dp(3))
        })
        val rows = if (keys.symbols) ImeKeys.punctuation else ImeKeys.letters
        rows.forEach { chars ->
            val row = row(root)
            chars.forEach { char ->
                val value = keys.character(char)
                key(row, value) {
                    if (acceptsInput) currentInputConnection?.commitText(value, 1)
                    keys.consumed(); render()
                }
            }
        }
        row(root).also { row ->
            key(row, if (keys.shift) "Shift ↑" else "Shift") { keys.shift = !keys.shift; render() }
            key(row, if (keys.caps) "Caps ●" else "Caps") { keys.caps = !keys.caps; render() }
            key(row, "←") { edit(KeyEvent.KEYCODE_DPAD_LEFT) }
            key(row, "→") { edit(KeyEvent.KEYCODE_DPAD_RIGHT) }
            key(row, "⌫ Delete") { edit(KeyEvent.KEYCODE_DEL) }
        }
        row(root).also { row ->
            key(row, if (keys.symbols) "ABC" else "?123") { keys.symbols = !keys.symbols; keys.shift = false; render() }
            key(row, "Keyboard") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
            key(row, "Space", 2f) {
                if (acceptsInput) currentInputConnection?.commitText(" ", 1)
                keys.consumed(); render()
            }
            key(row, actionLabel()) { enter() }
            key(row, "Hide") { requestHideSelf(0) }
        }
    }
    private fun row(root: LinearLayout) = LinearLayout(this).also {
        it.orientation = LinearLayout.HORIZONTAL
        root.addView(it, LinearLayout.LayoutParams(-1, -2))
    }
    private fun key(row: LinearLayout, label: String, weight: Float = 1f, action: () -> Unit) {
        val height = if (resources.configuration.screenHeightDp < 450) 34 else 44
        row.addView(Button(this).apply {
            text = label; contentDescription = when (label) { "←" -> "Move left"; "→" -> "Move right"; else -> label }
            textSize = if (label.length > 4) 11f else 16f
            isAllCaps = false; isSaveEnabled = false
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0); setTextColor(ink)
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(5).toFloat(); setStroke(dp(1), Color.rgb(210, 224, 215)) }
            backgroundTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(Color.rgb(200, 229, 214), Color.WHITE))
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(0, dp(height), weight).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
    }
    private fun edit(code: Int) { if (acceptsInput) sendDownUpKeyEvents(code) }
    private fun actionLabel(): String {
        val options = currentInputEditorInfo?.imeOptions ?: return "Enter"
        if (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "Enter"
        return when (options and EditorInfo.IME_MASK_ACTION) {
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_SEND -> "Send"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_PREVIOUS -> "Previous"
        EditorInfo.IME_ACTION_DONE -> "Done"
        else -> "Enter"
        }
    }
    private fun enter() {
        if (!acceptsInput) return
        val editor = currentInputEditorInfo ?: return
        val connection = currentInputConnection ?: return
        val action = editor.imeOptions and EditorInfo.IME_MASK_ACTION
        if (editor.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            if (!connection.performEditorAction(action)) connection.commitText("\n", 1)
        } else connection.commitText("\n", 1)
    }
}
