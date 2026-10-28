package com.example.inputmonitor.platform.ime

/** Small offline fallback when the device has no enabled spell checker. */
internal object LocalSuggestions {
    private val words = ("the be to of and a in that have I it for not on with he as you do at this but his by from " +
        "they we say her she or an will my one all would there their what so up out if about who get which go me " +
        "when make can like time no just him know take people into year your good some could them see other than " +
        "then now look only come its over think also back after use two how our work first well way even new want " +
        "because any these give day most us hello help here happy home hope how are thanks thank tomorrow today " +
        "please sorry welcome morning evening night yes okay love really right great going getting meeting message " +
        "friend family phone keyboard suggestion autocorrect emoji beautiful wonderful where why when before again").split(" ").distinct()
    private val typos = mapOf("teh" to "the", "adn" to "and", "thier" to "their", "recieve" to "receive",
        "becuase" to "because", "wiht" to "with", "helo" to "hello", "tommorow" to "tomorrow",
        "definately" to "definitely", "dont" to "don't", "cant" to "can't", "im" to "I'm")

    fun correction(word: String): String? = typos[word.lowercase()]?.let { matchCase(word, it) }
    fun suggest(word: String): List<String> {
        if (word.length < 2) return emptyList()
        return (listOfNotNull(correction(word)) + words.filter {
            it.startsWith(word, ignoreCase = true) && !it.equals(word, ignoreCase = true)
        }.map { matchCase(word, it) }).distinct().take(3)
    }
    private fun matchCase(word: String, value: String): String = when {
        word.all { it.isUpperCase() } -> value.uppercase()
        word.firstOrNull()?.isUpperCase() == true -> value.replaceFirstChar { it.uppercase() }
        else -> value
    }
}
