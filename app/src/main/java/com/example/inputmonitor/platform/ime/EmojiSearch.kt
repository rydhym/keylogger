package com.example.inputmonitor.platform.ime

internal object EmojiSearch {
    private fun withoutTone(value: String): String {
        val points = value.codePoints().toArray().filter { it !in 0x1F3FB..0x1F3FF }.toIntArray()
        return String(points, 0, points.size)
    }

    fun variants(emoji: String): List<String> {
        val base = withoutTone(emoji)
        return EmojiCatalog.categories.asSequence().flatMap { it.emoji.asSequence() }
            .filter { withoutTone(it) == base }.toList()
    }
    private val descriptions by lazy {
        EmojiCatalog.categories.flatMap { category -> category.emoji.map { emoji ->
            emoji to (category.name + " " + description(emoji)).lowercase()
        } }
    }
    fun description(emoji: String): String = emoji.codePoints().toArray()
        .filter { it != 0xFE0F && it != 0x200D }
        .joinToString(" ") { Character.getName(it).orEmpty().replace('_', ' ') }
        .ifBlank { emoji }

    fun find(query: String): List<String> {
        val words = query.lowercase().trim().split(Regex("\\s+"))
        return descriptions.filter { (emoji, name) -> query.trim() == emoji || words.all { name.contains(it) } }
            .map { it.first }
    }
}
