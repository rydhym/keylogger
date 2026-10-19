package app.cleartrail

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeKeysTest {
    @Test fun caseUsesShiftXorCapsAndShiftIsOneShot() {
        val keys = ImeKeys()
        assertEquals("a", keys.character('a'))
        keys.shift = true; assertEquals("A", keys.character('a'))
        keys.consumed(); assertEquals("a", keys.character('a'))
        keys.caps = true; assertEquals("A", keys.character('a'))
        keys.shift = true; assertEquals("a", keys.character('a'))
        keys.reset(); assertEquals("a", keys.character('a'))
    }
    @Test fun everyShiftedSymbolIsMappedAndCapsDoesNotShiftDigits() {
        val keys = ImeKeys(); keys.shift = true
        ImeKeys.plain.forEachIndexed { i, char -> assertEquals(ImeKeys.shifted[i].toString(), keys.character(char)) }
        keys.shift = false; keys.caps = true; assertEquals("1", keys.character('1'))
        keys.reset(true); assertEquals(true, keys.symbols)
        assertEquals(false, keys.caps); assertEquals(false, keys.shift)
    }
    @Test fun layoutsIncludeAllLettersDigitsAndAsciiPunctuation() {
        val letters = ImeKeys.letters.joinToString("")
        ('a'..'z').forEach { assertEquals(true, it in letters) }
        val symbols = ImeKeys.punctuation.joinToString("")
        ('!'..'~').filterNot { it.isLetter() }.forEach { assertEquals("Missing $it", true, it in symbols) }
    }
}
