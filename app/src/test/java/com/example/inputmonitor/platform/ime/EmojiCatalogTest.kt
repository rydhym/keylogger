package com.example.inputmonitor.platform.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {
    @Test
    fun includesFullUnicodeCatalogAndVariants() {
        val entries = EmojiCatalog.categories.flatMap { it.emoji }
        assertEquals(3963, entries.size)
        assertEquals(entries.size, entries.distinct().size)
        assertTrue(entries.containsAll(listOf("👍🏿", "🇮🇳", "👩‍💻", "🫠")))
    }
    @Test
    fun everyOfferedEmojiDeletesAsOneUnit() {
        EmojiCatalog.categories.flatMap { it.emoji }.forEach { emoji ->
            assertEquals(emoji, emoji.length, EmojiCatalog.deletionLength("hello$emoji"))
        }
    }

    @Test
    fun deletesOnlyLastEmojiOrCharacter() {
        assertEquals(2, EmojiCatalog.deletionLength("😀😀"))
        assertEquals(1, EmojiCatalog.deletionLength("😀a"))
        assertEquals(1, EmojiCatalog.deletionLength(""))
        assertEquals(2, EmojiCatalog.deletionLength("🦄"))
    }

    @Test
    fun catalogHasNoBlankEntriesAndFitsCursorWindow() {
        assertTrue(EmojiCatalog.categories.all { category ->
            category.emoji.isNotEmpty() && category.emoji.all { it.isNotBlank() && it.length <= 128 }
        })
    }
}
