package dev.motor.engine

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import java.net.URI

class Block(val text: CharSequence, val sizeSp: Float)
class PageLayout(val title: String, val blocks: List<Block>)

/** Transforma a árvore DOM em blocos de texto estilizados (layout simplificado: só block + inline). */
object LayoutBuilder {
    private val skip = setOf("head", "script", "style", "title", "template", "noscript")
    private val boldTags = setOf("b", "strong")
    private val italicTags = setOf("i", "em")
    private val blockTags = setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "pre", "blockquote",
        "section", "article", "header", "footer", "nav", "main", "table", "tr", "form"
    )
    private val heads = mapOf("h1" to 28f, "h2" to 24f, "h3" to 20f, "h4" to 18f, "h5" to 16f, "h6" to 14f)
    private const val FL = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    private val ws = Regex("\\s+")
    private const val MAX_BLOCKS = 3000
    private const val MAX_CHARS = 1_000_000

    fun build(root: Element, baseUrl: String): PageLayout {
        val b = Builder(baseUrl)
        b.title = Sanitize.text(findTitle(root) ?: "")
        b.walk(root, false, false, null)
        b.flush()
        return PageLayout(b.title, b.blocks)
    }

    private fun findTitle(n: Node): String? {
        if (n is Element) {
            if (n.tag == "title") return n.children.filterIsInstance<Text>().joinToString("") { it.text }.trim()
            for (c in n.children) findTitle(c)?.let { return it }
        }
        return null
    }

    private class Builder(val base: String) {
        val blocks = mutableListOf<Block>()
        var title = ""
        var cur = SpannableStringBuilder()
        var size = 16f

        var chars = 0
        val full: Boolean get() = blocks.size >= MAX_BLOCKS || chars > MAX_CHARS

        fun flush() {
            if (cur.isNotBlank() && !full) { blocks.add(Block(cur, size)); chars += cur.length }
            cur = SpannableStringBuilder()
        }

        private fun resolve(h: String): String? {
            val t = h.trim().replace(" ", "%20")
            if (t.isEmpty() || t.startsWith("#")) return null
            // só links http(s) válidos; javascript:, data:, file:, intent:, etc. viram texto comum
            return try { UrlGuard.check(URI(base).resolve(t).toString()) } catch (e: Exception) { null }
        }

        fun walk(n: Node, bold: Boolean, italic: Boolean, href: String?) {
            if (full) return
            when (n) {
                is Text -> {
                    if (cur.length > 20_000) flush()
                    var t = Sanitize.text(n.text).replace(ws, " ")
                    if (cur.isEmpty() || cur.endsWith(" ")) t = t.trimStart()
                    if (t.isEmpty()) return
                    val s = cur.length
                    cur.append(t)
                    val e = cur.length
                    if (bold) cur.setSpan(StyleSpan(Typeface.BOLD), s, e, FL)
                    if (italic) cur.setSpan(StyleSpan(Typeface.ITALIC), s, e, FL)
                    if (href != null) {
                        cur.setSpan(URLSpan(href), s, e, FL)
                        cur.setSpan(ForegroundColorSpan(0xFF1A0DAB.toInt()), s, e, FL)
                        cur.setSpan(UnderlineSpan(), s, e, FL)
                    }
                }
                is Element -> {
                    if (n.tag in skip) return
                    when (n.tag) {
                        "br" -> { flush(); return }
                        "hr" -> { flush(); cur.append("────────────"); flush(); return }
                        "img" -> {
                            val alt = n.attrs["alt"]
                            if (!alt.isNullOrBlank()) cur.append("[${Sanitize.text(alt)}]")
                            return
                        }
                    }
                    val isBlock = n.tag in blockTags || n.tag in heads
                    val saved = size
                    if (isBlock) {
                        flush()
                        size = heads[n.tag] ?: 16f
                        if (n.tag == "li") cur.append("• ")
                    }
                    val bd = bold || n.tag in boldTags || n.tag in heads
                    val ital = italic || n.tag in italicTags
                    var h = href
                    if (n.tag == "a") n.attrs["href"]?.let { h = resolve(it) }
                    for (c in n.children) walk(c, bd, ital, h)
                    if (isBlock) { flush(); size = saved }
                }
            }
        }
    }
}
