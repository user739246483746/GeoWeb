package dev.motor.engine

object CssParser {
    private const val MAX_RULES = 30000

    fun parse(src: String, origin: Int, vw: Float, set: RuleSet) {
        parseBlock(stripComments(src), origin, vw, set, 0)
    }

    private fun stripComments(s: String): String {
        if (!s.contains("/*")) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s.startsWith("/*", i)) {
                val e = s.indexOf("*/", i + 2)
                i = if (e < 0) s.length else e + 2
            } else { sb.append(s[i]); i++ }
        }
        return sb.toString()
    }

    private fun matchBrace(s: String, open: Int): Int {
        var d = 0
        var i = open
        var q = '\u0000'
        while (i < s.length) {
            val c = s[i]
            if (q != '\u0000') { if (c == q) q = '\u0000' }
            else if (c == '"' || c == '\'') q = c
            else if (c == '{') d++
            else if (c == '}') { d--; if (d == 0) return i }
            i++
        }
        return s.length
    }

    private fun parseBlock(css: String, origin: Int, vw: Float, set: RuleSet, depth: Int) {
        if (depth > 4) return
        var i = 0
        val n = css.length
        while (i < n) {
            if (set.count > MAX_RULES) return
            while (i < n && css[i].isWhitespace()) i++
            if (i >= n) break
            var j = i
            while (j < n && css[j] != '{' && !(css[i] == '@' && css[j] == ';')) j++
            if (j >= n) break
            if (css[j] == ';') { i = j + 1; continue }
            val end = matchBrace(css, j)
            val body = css.substring(j + 1, Math.min(end, n))
            val prelude = css.substring(i, j).trim()
            i = end + 1
            if (prelude.startsWith("@")) {
                val name = prelude.substring(1).takeWhile { it.isLetter() || it == '-' }.lowercase()
                val rest = prelude.substring(1 + name.length)
                if (name == "media") { if (mediaOk(rest, vw)) parseBlock(body, origin, vw, set, depth + 1) }
                else if (name == "supports" || name == "layer") parseBlock(body, origin, vw, set, depth + 1)
                continue
            }
            val decls = Css.parseDecls(body)
            if (decls.isEmpty()) continue
            for (s in Css.splitTop(prelude, ',')) {
                val sel = Selectors.parse(s.trim()) ?: continue
                set.add(Rule(sel, decls, origin, set.order++))
            }
        }
    }

    private val featRe = Regex("\\(([^()]*)\\)")

    fun mediaOk(q0: String, vw: Float): Boolean {
        val q = q0.trim().lowercase()
        if (q.isEmpty()) return true
        for (one in Css.splitTop(q, ',')) if (oneOk(one.trim(), vw)) return true
        return false
    }

    private fun oneOk(s: String, vw: Float): Boolean {
        if (s.startsWith("not ")) return false
        val bare = featRe.replace(s, " ")
        if (bare.contains("print") || bare.contains("speech")) return false
        for (m in featRe.findAll(s)) {
            val kv = m.groupValues[1].split(":", limit = 2)
            val k = kv[0].trim()
            val v = if (kv.size > 1) kv[1].trim() else ""
            when (k) {
                "min-width", "max-width" -> {
                    val l = Css.len(v, 16f, vw, 800f) ?: return false
                    if (k == "min-width" && vw < l.v) return false
                    if (k == "max-width" && vw > l.v) return false
                }
                "prefers-color-scheme" -> { if (v == "dark") return false }
                "hover" -> { if (v == "hover") return false }
                "pointer" -> { if (v == "fine") return false }
                "orientation" -> { if (v == "landscape") return false }
            }
        }
        return true
    }
}
