package com.example.inputmonitor.platform.ime

import org.junit.Assert.*
import org.junit.Test

class EmojiSearchTest {
    @Test fun findsDescriptionsAndExactEmoji() {
        assertTrue(EmojiSearch.find("pizza").contains("🍕"))
        assertTrue(EmojiSearch.find("🍕").contains("🍕"))
        assertTrue(EmojiSearch.find("no-such-emoji-description").isEmpty())
    }
    @Test fun groupsSkinTonesWithoutMixingGestures() {
        val variants = EmojiSearch.variants("👍🏽")
        assertEquals(6, variants.size)
        assertTrue(variants.containsAll(listOf("👍", "👍🏻", "👍🏿")))
        assertFalse(variants.contains("👎"))
    }
}
