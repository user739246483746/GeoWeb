package dev.motor.engine

import kotlin.math.roundToInt

object Css {
    const val CUR = 1   // marcador interno para "currentColor"

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun hx(c: Char): Int = Character.digit(c, 16)

    private fun funcArgs(s: String): List<String>? {
        val a = s.indexOf('(')
        val b = s.lastIndexOf(')')
        if (a < 0 || b <= a) return null
        return s.substring(a + 1, b).split(',', ' ', '/').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun comp(t: String): Float? =
        if (t.endsWith("%")) t.dropLast(1).toFloatOrNull()?.let { it * 2.55f } else t.toFloatOrNull()

    private fun alpha(t: String?): Int {
        if (t == null) return 255
        val f = if (t.endsWith("%")) (t.dropLast(1).toFloatOrNull() ?: 100f) / 100f else (t.toFloatOrNull() ?: 1f)
        return (f * 255f).roundToInt()
    }

    private fun hsl(h: Float, s: Float, l: Float): IntArray {
        val c = (1f - Math.abs(2f * l - 1f)) * s
        val hh = (((h % 360f) + 360f) % 360f) / 60f
        val x = c * (1f - Math.abs(hh % 2f - 1f))
        var r = 0f; var g = 0f; var b = 0f
        when (hh.toInt()) {
            0 -> { r = c; g = x }
            1 -> { r = x; g = c }
            2 -> { g = c; b = x }
            3 -> { g = x; b = c }
            4 -> { r = x; b = c }
            else -> { r = c; b = x }
        }
        val m = l - c / 2f
        return intArrayOf(((r + m) * 255f).roundToInt(), ((g + m) * 255f).roundToInt(), ((b + m) * 255f).roundToInt())
    }

    /** ARGB, 0 = transparent, CUR = currentColor, null = inválido */
    fun color(s0: String): Int? {
        val s = s0.trim().lowercase()
        if (s.isEmpty()) return null
        if (s == "transparent") return 0
        if (s == "currentcolor") return CUR
        if (s[0] == '#') {
            val h = s.substring(1)
            if (!h.all { it in '0'..'9' || it in 'a'..'f' }) return null
            return when (h.length) {
                3 -> argb(255, hx(h[0]) * 17, hx(h[1]) * 17, hx(h[2]) * 17)
                4 -> argb(hx(h[3]) * 17, hx(h[0]) * 17, hx(h[1]) * 17, hx(h[2]) * 17)
                6 -> argb(255, h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
                8 -> argb(h.substring(6, 8).toInt(16), h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
                else -> null
            }
        }
        if (s.startsWith("rgb")) {
            val a = funcArgs(s) ?: return null
            if (a.size < 3) return null
            val r = comp(a[0]) ?: return null
            val g = comp(a[1]) ?: return null
            val b = comp(a[2]) ?: return null
            return argb(alpha(a.getOrNull(3)), r.roundToInt(), g.roundToInt(), b.roundToInt())
        }
        if (s.startsWith("hsl")) {
            val a = funcArgs(s) ?: return null
            if (a.size < 3) return null
            val h = a[0].removeSuffix("deg").toFloatOrNull() ?: return null
            val sat = (a[1].removeSuffix("%").toFloatOrNull() ?: return null) / 100f
            val lig = (a[2].removeSuffix("%").toFloatOrNull() ?: return null) / 100f
            val rgb = hsl(h, sat, lig)
            return argb(alpha(a.getOrNull(3)), rgb[0], rgb[1], rgb[2])
        }
        val n = CssNames.map[s] ?: return null
        return 0xFF000000.toInt() or n
    }

    private val lenRe = Regex("^([+-]?(?:\\d+\\.?\\d*|\\.\\d+))([a-z%]*)$")

    fun len(s0: String, em: Float, vw: Float, vh: Float): Len? {
        val s = s0.trim().lowercase()
        if (s.isEmpty() || s == "auto") return null
        if (s == "0") return Len(0f, false)
        val m = lenRe.matchEntire(s) ?: return null
        val n = m.groupValues[1].toFloatOrNull() ?: return null
        return when (m.groupValues[2]) {
            "px" -> Len(n, false)
            "em" -> Len(n * em, false)
            "rem" -> Len(n * 16f, false)
            "%" -> Len(n, true)
            "vw" -> Len(n * vw / 100f, false)
            "vh" -> Len(n * vh / 100f, false)
            "pt" -> Len(n * 4f / 3f, false)
            "pc" -> Len(n * 16f, false)
            "cm" -> Len(n * 37.8f, false)
            "mm" -> Len(n * 3.78f, false)
            "in" -> Len(n * 96f, false)
            "ch", "ex" -> Len(n * em * 0.5f, false)
            else -> null
        }
    }

    fun tokens(v: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var depth = 0
        var q = '\u0000'
        for (ch in v) {
            if (q != '\u0000') { sb.append(ch); if (ch == q) q = '\u0000'; continue }
            when {
                ch == '"' || ch == '\'' -> { q = ch; sb.append(ch) }
                ch == '(' -> { depth++; sb.append(ch) }
                ch == ')' -> { depth--; sb.append(ch) }
                ch.isWhitespace() && depth <= 0 -> { if (sb.isNotEmpty()) { out.add(sb.toString()); sb.setLength(0) } }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    fun splitTop(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var depth = 0
        var q = '\u0000'
        for (ch in s) {
            if (q != '\u0000') { sb.append(ch); if (ch == q) q = '\u0000'; continue }
            when {
                ch == '"' || ch == '\'' -> { q = ch; sb.append(ch) }
                ch == '(' || ch == '[' -> { depth++; sb.append(ch) }
                ch == ')' || ch == ']' -> { depth--; sb.append(ch) }
                ch == sep && depth <= 0 -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(ch)
            }
        }
        out.add(sb.toString())
        return out
    }

    private val importantRe = Regex("!\\s*important\\s*$", RegexOption.IGNORE_CASE)

    fun parseDecls(block: String): List<Decl> {
        val out = ArrayList<Decl>()
        for (part in splitTop(block, ';')) {
            val i = part.indexOf(':')
            if (i <= 0) continue
            val prop = part.substring(0, i).trim().lowercase()
            var v = part.substring(i + 1).trim()
            var imp = false
            val m = importantRe.find(v)
            if (m != null) { imp = true; v = v.substring(0, m.range.first).trim() }
            if (prop.isEmpty() || v.isEmpty()) continue
            out.add(Decl(prop, v, imp))
        }
        return out
    }
}
