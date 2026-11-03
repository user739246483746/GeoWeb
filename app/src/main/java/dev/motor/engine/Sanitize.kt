package dev.motor.engine

object Sanitize {
    private fun bad(c: Char) =
        (c.isISOControl() && c != '\n' && c != '\t') ||
            c == '\u200E' || c == '\u200F' || c == '\u061C' ||
            c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069'

    fun text(s: String): String {
        if (s.none { bad(it) }) return s
        val sb = StringBuilder(s.length)
        for (c in s) if (!bad(c)) sb.append(c)
        return sb.toString()
    }
}
