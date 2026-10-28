package com.example.inputmonitor.platform.ime

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.textservice.*

/** Transient editor-only suggestions; never persisted or sent to the event publisher. */
internal class TypingSuggestions(
    private val context: Context,
    private val connection: () -> InputConnection?,
    private val display: (List<String>) -> Unit
) : SpellCheckerSession.SpellCheckerSessionListener {
    private var session: SpellCheckerSession? = null
    private var generation = 0
    private var word = ""
    private var correction: String? = null
    private var enabled = false

    fun start(info: EditorInfo?) {
        close()
        val type = info?.inputType ?: 0
        enabled = SuggestionPolicy.allows(type, info?.imeOptions ?: 0)
        if (enabled) session = runCatching {
            (context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager)
                ?.newSpellCheckerSession(null, null, this, true)
        }.getOrNull()
    }

    private fun currentWord(): String = runCatching {
        val ic = connection() ?: return@runCatching ""
        if (!ic.getSelectedText(0).isNullOrEmpty() ||
            ic.getTextAfterCursor(1, 0)?.firstOrNull()?.let { it.isLetter() || it == '\'' } == true) {
            return@runCatching ""
        }
        ic.getTextBeforeCursor(64, 0)?.toString()
            ?.takeLastWhile { it.isLetter() || it == '\'' }.orEmpty()
    }.getOrDefault("")

    fun refresh() {
        generation++
        correction = null
        word = if (enabled) currentWord() else ""
        correction = if (enabled) LocalSuggestions.correction(word) else null
        display(if (enabled) candidates(LocalSuggestions.suggest(word)) else emptyList())
        if (word.length >= 2) runCatching {
            session?.getSuggestions(arrayOf(TextInfo(word, 0, generation)), 3, false)
        }
    }

    fun choose(value: String) {
        if (!enabled || word.isEmpty() || currentWord() != word) return
        val ic = connection() ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) return
        ic.beginBatchEdit()
        try {
            if (ic.deleteSurroundingText(word.length, 0)) ic.commitText(value, 1)
        } finally { ic.endBatchEdit() }
        refresh()
    }

    fun correctBeforeSpace() { correction?.let(::choose) }

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
        val result = results?.firstOrNull() ?: return
        if (!enabled || result.sequence != generation || currentWord() != word) return
        val values = ((0 until result.suggestionsCount).mapNotNull { result.getSuggestionAt(it) } + LocalSuggestions.suggest(word))
            .filter { it.isNotBlank() }.distinct().take(3)
        correction = LocalSuggestions.correction(word) ?: values.firstOrNull()?.takeIf {
            result.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS != 0 &&
                result.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO != 0
        }
        display(candidates(values))
    }

    private fun candidates(values: List<String>): List<String> =
        if (word.isEmpty()) emptyList() else (listOf(word) + values).distinct().take(3)

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) = Unit

    fun close() {
        enabled = false
        generation++
        runCatching { session?.close() }
        session = null
        word = ""
        correction = null
        display(emptyList())
    }
}
