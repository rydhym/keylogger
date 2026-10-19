package app.cleartrail

/** Static US layout and transient modifiers only. Never holds entered text. */
internal class ImeKeys {
    var shift = false
    var caps = false
    var symbols = false
    fun reset(numeric: Boolean = false) { shift = false; caps = false; symbols = numeric }
    fun character(base: Char): String = when {
        base in 'a'..'z' -> if (shift.xor(caps)) base.uppercaseChar().toString() else base.toString()
        shift && base in plain -> shifted[plain.indexOf(base)].toString()
        else -> base.toString()
    }
    fun consumed() { shift = false }
    companion object {
        const val plain = "1234567890-=[]\\;',./`"
        const val shifted = "!@#$%^&*()_+{}|:\"<>?~"
        val letters = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")
        val punctuation = listOf("1234567890", "!@#$%^&*()", "-_=+[]{}\\|", ";:'\",.<>/?`~")
    }
}
