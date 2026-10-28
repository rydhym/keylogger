package com.example.inputmonitor.platform.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Recycled, scrollable emoji palette. Search stays inside the IME, without taking editor focus. */
internal class EmojiPickerView(
    context: Context,
    private val send: (KeyAction) -> Unit,
    private val returnToKeyboard: () -> Unit
) : LinearLayout(context) {
    private val dark = KeyboardAppearance.isDark(context)
    private val ink = Color.parseColor(if (dark) "#E3E3E3" else "#444746")
    private val muted = Color.parseColor(if (dark) "#9AA0A6" else "#747775")
    private val accent = Color.parseColor(if (dark) "#454746" else "#C2D7FC")
    private var category = 0
    private var query = ""
    private var searching = false
    private var entries = emptyList<String>()
    private val heading = TextView(context)
    private val search = TextView(context)
    private val grid = GridView(context)
    private val tabs = LinearLayout(context)
    private val searchKeys = LinearLayout(context)
    private val adapter = object : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View =
            ((recycled as? TextView) ?: label("", 28f).apply {
                layoutParams = AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, dp(44))
            }).apply {
                text = entries[position]
                contentDescription = EmojiSearch.description(entries[position])
            }
    }

    init {
        orientation = VERTICAL
        isFocusable = false
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("‹", "Return to letters", returnToKeyboard), LayoutParams(dp(40), dp(42)))
        search.apply {
            text = "⌕  Search emoji"
            textSize = 15f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(12), 0)
            background = rounded(if (dark) Color.parseColor("#303134") else Color.parseColor("#DDE1E5"))
            setOnClickListener { searching = !searching; update() }
            contentDescription = "Search emoji"
        }
        header.addView(search, LayoutParams(0, dp(36), 1f))
        header.addView(button("×", "Close emoji picker", returnToKeyboard), LayoutParams(dp(40), dp(42)))
        addView(header)
        heading.apply {
            setTextColor(muted); textSize = 11f
            setPadding(dp(12), dp(5), 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(heading, LayoutParams(LayoutParams.MATCH_PARENT, dp(24)))
        grid.apply {
            numColumns = 8
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = dp(2)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            adapter = this@EmojiPickerView.adapter
            setOnItemClickListener { _, view, position, _ ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                commit(entries[position])
            }
            setOnItemLongClickListener { _, view, position, _ ->
                val variants = EmojiSearch.variants(entries[position])
                if (variants.size < 2) return@setOnItemLongClickListener false
                val options = LinearLayout(context).apply {
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    background = rounded(if (dark) Color.parseColor("#3C4043") else Color.WHITE)
                }
                val popup = PopupWindow(options, LayoutParams.WRAP_CONTENT, dp(52), true).apply {
                    elevation = dp(8).toFloat()
                    setBackgroundDrawable(rounded(if (dark) Color.parseColor("#3C4043") else Color.WHITE))
                    isOutsideTouchable = true
                }
                variants.take(6).forEach { emoji ->
                    options.addView(button(emoji, EmojiSearch.description(emoji)) {
                        commit(emoji); popup.dismiss()
                    }.apply { textSize = 26f }, LayoutParams(dp(42), dp(44)))
                }
                popup.showAsDropDown(view, 0, -view.height - dp(54))
                true
            }
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        searchKeys.orientation = VERTICAL
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { row ->
            val keys = LinearLayout(context)
            row.forEach { char -> keys.addView(button(char.toString(), char.toString()) {
                query += char; updateResults()
            }, LayoutParams(0, dp(30), 1f)) }
            searchKeys.addView(keys)
        }
        addView(searchKeys)
        tabs.orientation = HORIZONTAL
        val strip = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        }
        addView(strip, LayoutParams(LayoutParams.MATCH_PARENT, dp(38)))
        val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(button("ABC", "Return to letters", returnToKeyboard), LayoutParams(dp(60), dp(42)))
        footer.addView(button("☺", "Emoji categories") { searching = false; update() }, LayoutParams(dp(44), dp(38)))
        footer.addView(button("space", "Space") {
            if (searching) { query += " "; updateResults() } else send(KeyAction.Space)
        }, LayoutParams(0, dp(38), 1f))
        footer.addView(button("⌫", "Delete") {
            if (searching) { query = query.dropLast(1); updateResults() } else send(KeyAction.Backspace)
        }, LayoutParams(dp(52), dp(42)))
        addView(footer)
        update()
    }

    private fun commit(emoji: String) {
        send(KeyAction.Character(emoji))
        recent.remove(emoji); recent.add(0, emoji)
        if (recent.size > 48) recent.removeAt(recent.lastIndex)
    }

    private fun update() {
        searchKeys.visibility = if (searching) View.VISIBLE else View.GONE
        tabs.parent.let { (it as View).visibility = if (searching) View.GONE else View.VISIBLE }
        tabs.removeAllViews()
        val categories = listOf("◷" to "Recently used") + EmojiCatalog.categories.map { it.icon to it.name }
        categories.forEachIndexed { index, (icon, name) ->
            tabs.addView(button(icon, name) { category = index - 1; searching = false; query = ""; update() }.apply {
                textSize = 20f
                if (category == index - 1) background = rounded(accent)
                else alpha = 0.65f
            }, LayoutParams(dp(39), dp(34)))
        }
        updateResults()
        grid.setSelection(0)
    }

    private fun updateResults() {
        search.text = if (searching) "⌕  ${query.ifEmpty { "Type to search…" }}" else "⌕  Search emoji"
        entries = when {
            searching && query.isNotBlank() -> EmojiSearch.find(query)
            category < 0 -> recent.toList()
            else -> EmojiCatalog.categories[category].emoji
        }
        heading.text = when {
            searching && query.isNotBlank() -> if (entries.isEmpty()) "No emoji found" else "Search results"
            category < 0 -> if (entries.isEmpty()) "Your recent emojis will appear here" else "Recently used"
            else -> EmojiCatalog.categories[category].name
        }
        adapter.notifyDataSetChanged()
    }

    private fun label(value: String, size: Float) = TextView(context).apply {
        text = value; textSize = size; gravity = Gravity.CENTER
        setTextColor(ink); isFocusable = false
        includeFontPadding = false
    }
    private fun button(value: String, description: String, click: () -> Unit) = label(value, 16f).apply {
        contentDescription = description
        setOnClickListener { click() }
        val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
        background = attrs.getDrawable(0)
        attrs.recycle()
    }
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(22).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        // Session-only recents; typed emoji are not saved in keyboard preferences.
        private val recent = mutableListOf<String>()
    }
}
