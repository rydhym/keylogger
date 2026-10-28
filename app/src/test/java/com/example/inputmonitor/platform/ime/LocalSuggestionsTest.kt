package com.example.inputmonitor.platform.ime

import org.junit.Assert.*
import org.junit.Test

class LocalSuggestionsTest {
    @Test fun correctsKnownTyposAndPreservesCase() {
        assertEquals("the", LocalSuggestions.correction("teh"))
        assertEquals("Hello", LocalSuggestions.correction("Helo"))
        assertEquals("THE", LocalSuggestions.correction("TEH"))
        assertNull(LocalSuggestions.correction("Gaurav"))
    }
    @Test fun offersCompletionsWithBoundedResults() {
        assertTrue(LocalSuggestions.suggest("he").contains("hello"))
        assertTrue(LocalSuggestions.suggest("a").isEmpty())
        assertTrue(LocalSuggestions.suggest("th").size <= 3)
        assertEquals("the", LocalSuggestions.suggest("teh").first())
    }
}
