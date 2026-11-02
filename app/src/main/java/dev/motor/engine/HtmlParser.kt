package dev.motor.engine

/** Tokenizer + construtor de árvore simplificado (não é o algoritmo completo do HTML5). */
object HtmlParser {
    private val voids = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr")
    private val rawText = setOf("script", "style")

    fun parse(html: String): Element {
        val root = Element("#root", emptyMap())
        var cur = root
        var i = 0
        val n = html.length
        while (i < n) {
            if (html[i] != '<') {
                val e = html.indexOf('<', i)
                val end = if (e < 0) n else e
                cur.children.add(Text(decode(html.substring(i, end))))
                i = end
                continue
            }
            when {
                html.startsWith("<!--", i) -> {
                    val e = html.indexOf("-->", i + 4)
                    i = if (e < 0) n else e + 3
                }
                html.startsWith("<!", i) || html.startsWith("<?", i) -> {
                    val e = html.indexOf('>', i)
                    i = if (e < 0) n else e + 1
                }
                html.startsWith("</", i) -> {
                    val e = html.indexOf('>', i)
                    val end = if (e < 0) n else e
                    val name = html.substring(i + 2, end).trim().lowercase()
                    var p: Element? = cur
                    while (p != null && p.tag != name) p = p.parent
                    if (p != null && p !== root) cur = p.parent ?: root
                    i = if (e < 0) n else e + 1
                }
                i + 1 < n && html[i + 1].isLetter() -> {
                    var j = i + 1
                    while (j < n && !html[j].isWhitespace() && html[j] != '>' && html[j] != '/') j++
                    val name = html.substring(i + 1, j).lowercase()
                    val attrs = LinkedHashMap<String, String>()
                    while (j < n && html[j] != '>') {
                        val c = html[j]
                        if (c.isWhitespace() || c == '/') { j++; continue }
                        val s = j
                        while (j < n && !html[j].isWhitespace() && html[j] !in "=>/") j++
                        val an = html.substring(s, j).lowercase()
                        while (j < n && html[j].isWhitespace()) j++
                        var v = ""
                        if (j < n && html[j] == '=') {
                            j++
                            while (j < n && html[j].isWhitespace()) j++
                            if (j < n && (html[j] == '"' || html[j] == '\'')) {
                                val q = html[j]
                                val e = html.indexOf(q, j + 1)
                                val ee = if (e < 0) n else e
                                v = html.substring(j + 1, ee)
                                j = if (e < 0) n else e + 1
                            } else {
                                val vs = j
                                while (j < n && !html[j].isWhitespace() && html[j] != '>') j++
                                v = html.substring(vs, j)
                            }
                        }
                        if (an.isNotEmpty()) attrs[an] = decode(v)
                    }
                    i = if (j < n) j + 1 else n

                    // fechamentos implícitos básicos
                    if ((name == "p" && cur.tag == "p") || (name == "li" && cur.tag == "li")) {
                        cur = cur.parent ?: root
                    }
                    val el = Element(name, attrs, cur)
                    cur.children.add(el)
                    if (name in rawText) {
                        val close = html.indexOf("</$name", i, ignoreCase = true)
                        val ce = if (close < 0) n else close
                        if (name == "style") el.children.add(Text(html.substring(i, ce))) // para o parser CSS futuro
                        i = if (close < 0) n else html.indexOf('>', close).let { if (it < 0) n else it + 1 }
                    } else if (name !in voids) {
                        cur = el
                    }
                }
                else -> { cur.children.add(Text("<")); i++ }
            }
        }
        return root
    }

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to "\u00a0", "copy" to "©", "hellip" to "…", "mdash" to "—", "ndash" to "–"
    )
    private val entRe = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    private fun cp(v: Int?, orig: String): String =
        if (v != null && Character.isValidCodePoint(v)) String(Character.toChars(v)) else orig

    fun decode(s: String): String {
        if ('&' !in s) return s
        return entRe.replace(s) { m ->
            val g = m.groupValues[1]
            when {
                g.startsWith("#x") -> cp(g.substring(2).toIntOrNull(16), m.value)
                g.startsWith("#") -> cp(g.substring(1).toIntOrNull(), m.value)
                else -> named[g] ?: m.value
            }
        }
    }
}
