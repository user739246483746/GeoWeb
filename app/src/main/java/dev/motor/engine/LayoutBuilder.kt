package dev.motor.engine
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import java.net.URI
class PageLayout(val title: String, val root: BlockBox, val bg: Int, val images: List<ImageBox>)
class SheetRef(val css: String?, val href: String?)
private const val MAX_BOXES = 30000
private const val MAX_IMAGES = 40
private const val FL = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
private val WS = Regex("\\s+")
private val UA_CSS = """
html,body,div,p,h1,h2,h3,h4,h5,h6,ul,ol,dl,dt,dd,pre,blockquote,section,article,header,footer,nav,main,aside,figure,figcaption,form,fieldset,address,details,summary,hgroup,center,table,thead,tbody,tfoot,caption,legend,menu,dir,optgroup{display:block}
head,script,style,title,template,noscript,meta,link,base,area,datalist,dialog,object,param,audio,video,canvas,svg,iframe,embed,select,input,textarea,map,source,track,frame,frameset,[hidden]{display:none}
li{display:list-item}
body{margin:8px}
h1{font-size:2em;margin:.67em 0;font-weight:bold}
h2{font-size:1.5em;margin:.83em 0;font-weight:bold}
h3{font-size:1.17em;margin:1em 0;font-weight:bold}
h4{margin:1.33em 0;font-weight:bold}
h5{font-size:.83em;margin:1.67em 0;font-weight:bold}
h6{font-size:.67em;margin:2.33em 0;font-weight:bold}
p,dl,fieldset,figure{margin:1em 0}
blockquote{margin:1em 32px}
ul,ol{margin:1em 0;padding-left:24px}
ul ul,ul ol,ol ul,ol ol{margin:0}
dd{margin-left:32px}
pre{font-family:monospace;white-space:pre;margin:1em 0}
code,kbd,samp,tt{font-family:monospace}
a{color:#1a0dab;text-decoration:underline}
b,strong,th,summary,dt{font-weight:bold}
i,em,cite,dfn,var,address{font-style:italic}
u,ins{text-decoration:underline}
s,strike,del{text-decoration:line-through}
small,sub,sup{font-size:.83em}
big{font-size:1.17em}
center,th{text-align:center}
hr{display:block;border-top:1px solid #bbb;margin:8px 0}
mark{background-color:#ff0}
tr{display:flex}
td,th{display:block;flex:1 1 0;padding:2px}
button{display:inline-block;background-color:#efefef}
img{display:inline-block}
"""
object LayoutBuilder {
  fun resolve(base: String, h: String): String? {
    val t = h.trim().replace(" ", "%20")
    if (t.isEmpty() || t.startsWith("#")) return null
    return try { UrlGuard.check(URI(base).resolve(t).toString()) } catch (e: Exception) { null }
  }
  fun sheetRefs(dom: Element, base: String): List<SheetRef> {
    val out = ArrayList<SheetRef>()
    fun visit(e: Element) {
      if (e.tag == "style") {
        val m = (e.attrs["media"] ?: "").lowercase()
        if (!(m.contains("print") && !m.contains("screen"))) {
          out.add(SheetRef(e.children.filterIsInstance<Text>().joinToString("") { it.text }, null))
        }
      } else if (e.tag == "link") {
        val rel = (e.attrs["rel"] ?: "").lowercase().split(' ')
        val href = e.attrs["href"]
        val m = (e.attrs["media"] ?: "").lowercase()
        if (rel.contains("stylesheet") && href != null && !(m.contains("print") && !m.contains("screen"))) {
          val u = resolve(base, href)
          if (u != null) out.add(SheetRef(null, u))
        }
      }
      for (c in e.children) if (c is Element) visit(c)
    }
    visit(dom)
    return out
  }
  fun build(dom: Element, base: String, sheets: List<String>, vw: Float, vh: Float): PageLayout {
    val rs = RuleSet()
    CssParser.parse(UA_CSS, 0, vw, rs)
    var total = 0
    for (s in sheets) {
      if (total > 1_500_000) break
      total += s.length
      CssParser.parse(s, 1, vw, rs)
    }
    val b = Builder(base, Styler(rs, vw, vh))
    val rootStyle = Style()
    rootStyle.display = D.BLOCK
    val rootBox = BlockBox(rootStyle)
    val ctx = Ctx(rootBox, rootStyle)
    b.walk(dom, rootStyle, ctx, null)
    b.flush(ctx)
    return PageLayout(Sanitize.text(findTitle(dom) ?: ""), rootBox, b.bg, b.images)
  }
  fun plain(title: String, text: String): PageLayout {
    val rs = Style()
    rs.display = D.BLOCK
    for (i in 0..3) rs.padding[i] = Len(16f, false)
    val box = BlockBox(rs)
    box.children.add(TextBox(Sanitize.text(text), Style()))
    return PageLayout(title, box, 0, emptyList())
  }
  private fun findTitle(n: Node): String? {
    if (n is Element) {
      if (n.tag == "title") return n.children.filterIsInstance<Text>().joinToString("") { it.text }.trim()
      for (c in n.children) findTitle(c)?.let { return it }
    }
    return null
  }
}
private class Ctx(val box: BlockBox, val st: Style) {
  var run: SpannableStringBuilder? = null
}
private class Builder(val base: String, val styler: Styler) {
  val images = ArrayList<ImageBox>()
  var bg = 0
  var boxes = 0
  fun flush(ctx: Ctx) {
    val r = ctx.run ?: return
    ctx.run = null
    if (!ctx.st.pre) {
      while (r.isNotEmpty() && (r[r.length - 1] == ' ' || r[r.length - 1] == '\n')) r.delete(r.length - 1, r.length)
    }
    if (r.isEmpty() || (!ctx.st.pre && r.isBlank())) return
    ctx.box.children.add(TextBox(r, ctx.st))
    boxes++
  }
  private fun transform(t: String, m: Int): String = when (m) {
    1 -> t.uppercase()
    2 -> t.lowercase()
    3 -> {
      val sb = StringBuilder(t.length)
      var up = true
      for (ch in t) {
        sb.append(if (up) ch.uppercaseChar() else ch)
        up = ch == ' ' || ch == '\n'
      }
      sb.toString()
    }
    else -> t
  }
  private fun spans(run: SpannableStringBuilder, a: Int, b: Int, s: Style, base: Style, href: String?) {
    if (s.color != base.color) run.setSpan(ForegroundColorSpan(s.color), a, b, FL)
    if (Math.abs(s.fontSize - base.fontSize) > 0.05f) run.setSpan(RelativeSizeSpan(s.fontSize / base.fontSize), a, b, FL)
    if (s.bold && s.italic) run.setSpan(StyleSpan(Typeface.BOLD_ITALIC), a, b, FL)
    else if (s.bold) run.setSpan(StyleSpan(Typeface.BOLD), a, b, FL)
    else if (s.italic) run.setSpan(StyleSpan(Typeface.ITALIC), a, b, FL)
    if (s.underline) run.setSpan(UnderlineSpan(), a, b, FL)
    if (s.strike) run.setSpan(StrikethroughSpan(), a, b, FL)
    if (s.family == 2) run.setSpan(TypefaceSpan("monospace"), a, b, FL)
    else if (s.family == 1) run.setSpan(TypefaceSpan("serif"), a, b, FL)
    if (s.bg != 0 && (s.display == D.INLINE || s.display == D.INLINE_BLOCK)) run.setSpan(BackgroundColorSpan(s.bg), a, b, FL)
    if (href != null) run.setSpan(URLSpan(href), a, b, FL)
  }
  private fun appendText(ctx: Ctx, s: Style, raw: String, href: String?) {
    var t = Sanitize.text(raw)
    t = if (s.pre) t.replace("\r", "") else t.replace(WS, " ")
    t = transform(t, s.transform)
    var run = ctx.run
    if (!s.pre && (run == null || run.isEmpty() || run[run.length - 1] == ' ' || run[run.length - 1] == '\n')) t = t.trimStart(' ')
    if (t.isEmpty()) return
    if (run != null && run.length > 20000) { flush(ctx); run = null; if (!s.pre) t = t.trimStart(' ') }
    if (run == null) { run = SpannableStringBuilder(); ctx.run = run }
    val a = run.length
    run.append(t)
    spans(run, a, run.length, s, ctx.st, href)
  }
  private fun appendBreak(ctx: Ctx) {
    var run = ctx.run
    if (run == null) { run = SpannableStringBuilder(); ctx.run = run }
    run.append('\n')
  }
  private fun num(s: String?): Float = s?.trim()?.removeSuffix("px")?.toFloatOrNull() ?: 0f
  private fun pickSrc(e: Element): String? {
    var s = e.attrs["src"]
    val ds = e.attrs["data-src"] ?: e.attrs["data-lazy-src"] ?: e.attrs["data-original"]
    if (ds != null && ds.isNotBlank() && (s == null || s.isBlank() || (s.startsWith("data:") && s.length < 400))) s = ds
    if (s == null || s.isBlank()) {
      val ss = e.attrs["srcset"] ?: e.attrs["data-srcset"]
      if (ss != null) s = ss.trim().split(',')[0].trim().split(' ')[0]
    }
    return s?.trim()
  }
  private fun makeImage(e: Element, cs: Style, href: String?): ImageBox {
    val raw = pickSrc(e)
    var u: String? = null
    if (raw != null) {
      u = if (raw.startsWith("data:image/")) (if (raw.length <= 6_000_000) raw else null) else LayoutBuilder.resolve(base, raw)
    }
    val alt = Sanitize.text(e.attrs["alt"] ?: "").take(200)
    val box = ImageBox(cs, u, alt, num(e.attrs["width"]), num(e.attrs["height"]))
    box.href = href
    if (u != null && images.size < MAX_IMAGES) images.add(box)
    boxes++
    return box
  }
  private fun makeBlock(e: Element, cs: Style, href: String?): BlockBox {
    val box = BlockBox(cs)
    boxes++
    val ctx = Ctx(box, cs)
    if (cs.display == D.LIST || e.tag == "li") marker(e, cs, ctx, href)
    walk(e, cs, ctx, href)
    flush(ctx)
    return box
  }
  private fun marker(e: Element, cs: Style, ctx: Ctx, href: String?) {
    if (cs.listNone) return
    val p = e.parent
    if (p != null && p.tag == "ol") {
      var idx = 1
      for (c in p.children) { if (c === e) break; if (c is Element && c.tag == "li") idx++ }
      appendText(ctx, cs, "$idx. ", href)
    } else appendText(ctx, cs, "• ", href)
  }
  fun walk(el: Element, st: Style, ctx: Ctx, href: String?) {
    val blockify = st.display == D.FLEX || st.display == D.GRID
    for (c in el.children) {
      if (boxes > MAX_BOXES) return
      if (c is Text) {
        if (blockify && c.text.isBlank()) continue
        appendText(ctx, st, c.text, href)
        if (blockify) flush(ctx)
        continue
      }
      val e = c as Element
      val cs = styler.compute(e, st)
      if (cs.display == D.NONE || cs.hidden) continue
      if ((e.tag == "html" || e.tag == "body") && cs.bg != 0) bg = cs.bg
      val h = if (e.tag == "a") (e.attrs["href"]?.let { LayoutBuilder.resolve(base, it) } ?: href) else href
      if (e.tag == "br") {
        appendBreak(ctx)
      } else if (e.tag == "img") {
        flush(ctx)
        ctx.box.children.add(makeImage(e, cs, h))
      } else if (blockify || !(cs.display == D.INLINE || cs.display == D.INLINE_BLOCK)) {
        flush(ctx)
        ctx.box.children.add(makeBlock(e, cs, h))
      } else {
        walk(e, cs, ctx, h)
      }
    }
  }
}
