package dev.motor.engine

object Selectors {
    private val attrRe = Regex("""^([\w:-]+)\s*(?:([~|^$*]?=)\s*(.+?))?(?:\s+[iIsS])?$""")

    private fun isIdentChar(c: Char) = c.isLetterOrDigit() || c == '-' || c == '_' || c.code > 127
    private fun identEnd(s: String, from: Int): Int {
        var i = from
        while (i < s.length && isIdentChar(s[i])) i++
        return i
    }

    private fun parseAttr(b: String): AttrSel? {
        val m = attrRe.matchEntire(b.trim()) ?: return null
        var v = m.groupValues[3].trim()
        if (v.length >= 2 && (v[0] == '"' || v[0] == '\'') && v[v.length - 1] == v[0]) v = v.substring(1, v.length - 1)
        return AttrSel(m.groupValues[1].lowercase(), m.groupValues[2], v)
    }

    fun parse(s: String): Sel? {
        if (s.isEmpty()) return null
        val parts = ArrayList<Simple>()
        val combs = ArrayList<Char>()
        var spec = 0
        var i = 0
        val n = s.length
        while (i < n) {
            var sawSpace = false
            while (i < n && s[i].isWhitespace()) { i++; sawSpace = true }
            if (i >= n) break
            var comb = ' '
            if (s[i] == '>' || s[i] == '+' || s[i] == '~') {
                comb = s[i]; i++
                while (i < n && s[i].isWhitespace()) i++
                if (i >= n) return null
            } else if (parts.isNotEmpty() && !sawSpace) return null
            val sm = Simple()
            var any = false
            var first = true
            while (i < n) {
                val c = s[i]
                if (c == '*' && first) { i++; any = true }
                else if (c == '#') {
                    val e = identEnd(s, i + 1); if (e == i + 1) return null
                    sm.id = s.substring(i + 1, e); i = e; any = true; spec += 10000
                }
                else if (c == '.') {
                    val e = identEnd(s, i + 1); if (e == i + 1) return null
                    sm.classes.add(s.substring(i + 1, e)); i = e; any = true; spec += 100
                }
                else if (c == '[') {
                    val e = s.indexOf(']', i); if (e < 0) return null
                    val a = parseAttr(s.substring(i + 1, e)) ?: return null
                    sm.attrs.add(a); i = e + 1; any = true; spec += 100
                }
                else if (c == ':') {
                    if (s.startsWith("::", i)) return null
                    val e = identEnd(s, i + 1)
                    if (e < n && s[e] == '(') return null
                    when (s.substring(i + 1, e).lowercase()) {
                        "first-child" -> sm.firstChild = true
                        "last-child" -> sm.lastChild = true
                        "root" -> sm.root = true
                        else -> return null
                    }
                    i = e; any = true; spec += 100
                }
                else if (first && (c.isLetter() || c == '_' || c == '-')) {
                    val e = identEnd(s, i)
                    sm.tag = s.substring(i, e).lowercase(); i = e; any = true; spec += 1
                }
                else break
                first = false
            }
            if (!any) return null
            if (parts.isNotEmpty()) combs.add(comb)
            parts.add(sm)
            if (i < n && !(s[i].isWhitespace() || s[i] == '>' || s[i] == '+' || s[i] == '~')) return null
        }
        if (parts.isEmpty()) return null
        return Sel(parts, combs, spec)
    }

    private fun prevSibling(el: Element): Element? {
        val p = el.parent ?: return null
        var prev: Element? = null
        for (c in p.children) {
            if (c === el) return prev
            if (c is Element) prev = c
        }
        return null
    }

    private fun attrMatch(a: AttrSel, el: Element): Boolean {
        val v = el.attrs[a.name] ?: return false
        return when (a.op) {
            "" -> true
            "=" -> v == a.value
            "~=" -> v.split(' ').contains(a.value)
            "|=" -> v == a.value || v.startsWith(a.value + "-")
            "^=" -> a.value.isNotEmpty() && v.startsWith(a.value)
            "$=" -> a.value.isNotEmpty() && v.endsWith(a.value)
            "*=" -> a.value.isNotEmpty() && v.contains(a.value)
            else -> false
        }
    }

    private fun simple(sm: Simple, el: Element): Boolean {
        val t = sm.tag
        if (t != null && t != el.tag) return false
        val id = sm.id
        if (id != null && id != el.id) return false
        for (c in sm.classes) if (!el.classList.contains(c)) return false
        for (a in sm.attrs) if (!attrMatch(a, el)) return false
        if (sm.root && el.tag != "html") return false
        if (sm.firstChild) {
            val p = el.parent ?: return false
            if (p.children.firstOrNull { it is Element } !== el) return false
        }
        if (sm.lastChild) {
            val p = el.parent ?: return false
            if (p.children.lastOrNull { it is Element } !== el) return false
        }
        return true
    }

    fun matches(sel: Sel, el: Element): Boolean = matchAt(sel, sel.parts.size - 1, el)

    private fun matchAt(sel: Sel, i: Int, el: Element): Boolean {
        if (!simple(sel.parts[i], el)) return false
        if (i == 0) return true
        when (sel.combs[i - 1]) {
            '>' -> {
                val p = el.parent ?: return false
                return p.tag != "#root" && matchAt(sel, i - 1, p)
            }
            '+' -> {
                val s = prevSibling(el) ?: return false
                return matchAt(sel, i - 1, s)
            }
            '~' -> {
                var s = prevSibling(el)
                while (s != null) { if (matchAt(sel, i - 1, s)) return true; s = prevSibling(s) }
                return false
            }
            else -> {
                var p = el.parent
                while (p != null && p.tag != "#root") { if (matchAt(sel, i - 1, p)) return true; p = p.parent }
                return false
            }
        }
    }
}
