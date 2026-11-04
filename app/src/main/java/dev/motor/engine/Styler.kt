package dev.motor.engine
class Styler(private val rules: RuleSet, private val vw: Float, private val vh: Float) {
  private val varRe = Regex("var\\(\\s*(--[\\w-]+)\\s*(?:,\\s*([^)]*))?\\)")
  private val pxRe = Regex("(\\d+(?:\\.\\d+)?)px")
  private val colorTokRe = Regex("#[0-9a-f]{3,8}|rgba?\\([^)]*\\)|hsla?\\([^)]*\\)|[a-z]+")
  private val borderSideRe = Regex("border-(top|right|bottom|left)(-width|-color|-style)?")
  private val bStyles = setOf("solid", "dashed", "dotted", "double", "groove", "ridge", "inset", "outset")
  private val sizeKw = setOf("xx-small", "x-small", "small", "medium", "large", "x-large", "xx-large", "smaller", "larger")
  fun compute(el: Element, parent: Style): Style {
    val st = Style()
    st.inheritFrom(parent)
    val matched = rules.match(el)
    val inline = el.attrs["style"]?.let { Css.parseDecls(it) } ?: emptyList()
    val list = ArrayList<Decl>()
    for (r in matched) if (r.origin == 0) for (d in r.decls) if (!d.important) list.add(d)
    hints(el, list)
    for (r in matched) if (r.origin != 0) for (d in r.decls) if (!d.important) list.add(d)
    for (d in inline) if (!d.important) list.add(d)
    for (r in matched) for (d in r.decls) if (d.important) list.add(d)
    for (d in inline) if (d.important) list.add(d)
    var own: HashMap<String, String>? = null
    for (d in list) {
      if (d.prop.startsWith("--")) {
        if (own == null) own = HashMap(parent.vars)
        own[d.prop] = d.value
      }
    }
    val vars = own ?: parent.vars
    st.vars = vars
    for (d in list) {
      if (d.prop == "font-size" || d.prop == "font") applyProp(st, d.prop, sub(d.value, vars), parent)
    }
    for (d in list) {
      if (d.prop == "font-size" || d.prop == "font" || d.prop.startsWith("--")) continue
      applyProp(st, d.prop, sub(d.value, vars), parent)
    }
    return st
  }
  private fun sub(v: String, vars: Map<String, String>): String {
    if (!v.contains("var(")) return v
    var s = v
    var n = 0
    while (s.contains("var(") && n < 6) {
      s = varRe.replace(s) { m -> vars[m.groupValues[1]] ?: m.groupValues[2] }
      n++
    }
    return s
  }
  private fun hints(el: Element, out: ArrayList<Decl>) {
    val bg = el.attrs["bgcolor"]
    if (bg != null) out.add(Decl("background-color", fixHex(bg), false))
    when (el.tag) {
      "table", "td", "th" -> {
        val w = dim(el.attrs["width"])
        if (w != null) { out.add(Decl("width", w, false)); out.add(Decl("flex", "0 0 auto", false)) }
      }
      "font" -> {
        val c = el.attrs["color"]
        if (c != null) out.add(Decl("color", fixHex(c), false))
        val s = el.attrs["size"]?.trim()?.toIntOrNull()
        if (s != null) {
          val px = when {
            s <= 1 -> 10
            s == 2 -> 13
            s == 3 -> 16
            s == 4 -> 18
            s == 5 -> 24
            s == 6 -> 32
            else -> 48
          }
          out.add(Decl("font-size", "${px}px", false))
        }
      }
    }
  }
  private fun dim(v: String?): String? {
    if (v == null) return null
    val t = v.trim()
    if (t.endsWith("%")) return if (t.dropLast(1).toFloatOrNull() != null) t else null
    val f = t.toFloatOrNull() ?: return null
    return "${f}px"
  }
  private fun fixHex(v: String): String {
    val t = v.trim()
    return if (Regex("[0-9a-fA-F]{6}").matches(t)) "#$t" else t
  }
  private fun sides(t: List<String>): Array<String> = when (t.size) {
    0 -> arrayOf("", "", "", "")
    1 -> arrayOf(t[0], t[0], t[0], t[0])
    2 -> arrayOf(t[0], t[1], t[0], t[1])
    3 -> arrayOf(t[0], t[1], t[2], t[1])
    else -> arrayOf(t[0], t[1], t[2], t[3])
  }
  private fun setMargin(st: Style, i: Int, s: String) {
    if (s.trim().lowercase() == "auto") { st.marginAuto[i] = true; st.margin[i] = null; return }
    val l = Css.len(s, st.fontSize, vw, vh) ?: return
    st.marginAuto[i] = false
    st.margin[i] = l
  }
  private fun setPad(st: Style, i: Int, s: String) {
    val l = Css.len(s, st.fontSize, vw, vh) ?: return
    st.padding[i] = l
  }
  private fun sideIndex(n: String): Int = when (n) { "top" -> 0; "right" -> 1; "bottom" -> 2; else -> 3 }
  private fun borderWidthOf(tk: String, em: Float): Float? {
    val l = tk.lowercase()
    if (l == "thin") return 1f
    if (l == "medium") return 3f
    if (l == "thick") return 5f
    val ln = Css.len(tk, em, vw, vh) ?: return null
    return if (ln.pct) null else ln.v
  }
  private fun borderParse(st: Style, idx: IntArray, v: String) {
    var w = -1f
    var none = false
    var col: Int? = null
    for (tk in Css.tokens(v)) {
      val l = tk.lowercase()
      if (l == "none" || l == "hidden") { none = true; continue }
      if (l in bStyles) continue
      val bw = borderWidthOf(tk, st.fontSize)
      if (bw != null) { w = bw; continue }
      val c = Css.color(tk)
      if (c != null) col = c
    }
    val cc = col
    for (i in idx) {
      st.borderW[i] = if (none) 0f else if (w >= 0f) w else 3f
      st.borderC[i] = if (cc != null) cc else Css.CUR
    }
  }
  private fun displayOf(lv: String): Int {
    return when (lv.split(' ')[0]) {
      "none" -> D.NONE
      "block", "flow-root", "table", "table-row-group", "table-header-group", "table-footer-group",
      "table-caption", "table-cell", "contents" -> D.BLOCK
      "flex", "inline-flex", "table-row" -> D.FLEX
      "grid", "inline-grid" -> D.GRID
      "list-item" -> D.LIST
      "inline-block", "inline-table" -> D.INLINE_BLOCK
      else -> D.INLINE
    }
  }
  private fun familyOf(lv: String): Int {
    if (lv.contains("mono") || lv.contains("courier") || lv.contains("consolas") || lv.contains("menlo")) return 2
    if ((lv.contains("serif") && !lv.contains("sans")) || lv.startsWith("georgia") || lv.startsWith("times") || lv.startsWith("\"times")) return 1
    return 0
  }
  private fun gradientColor(lv: String): Int? {
    if (!lv.contains("gradient(")) return null
    for (m in colorTokRe.findAll(lv)) {
      val c = Css.color(m.value)
      if (c != null && c != Css.CUR && c != 0) return c
    }
    return null
  }
  private fun background(st: Style, lv: String) {
    var col: Int? = null
    for (tk in Css.tokens(lv)) {
      val c = Css.color(tk)
      if (c != null && c != Css.CUR) { col = c; break }
    }
    if (col == null) col = gradientColor(lv)
    st.bg = col ?: 0
  }
  private fun fontSize(st: Style, lv: String, parent: Style) {
    val kw: Float? = when (lv) {
      "xx-small" -> 9f
      "x-small" -> 10f
      "small" -> 13f
      "medium" -> 16f
      "large" -> 18f
      "x-large" -> 24f
      "xx-large" -> 32f
      "xxx-large" -> 48f
      "smaller" -> parent.fontSize / 1.2f
      "larger" -> parent.fontSize * 1.2f
      else -> null
    }
    if (kw != null) st.fontSize = kw
    else {
      val l = Css.len(lv, parent.fontSize, vw, vh)
      if (l != null) st.fontSize = if (l.pct) parent.fontSize * l.v / 100f else l.v
    }
    if (st.fontSize < 1f) st.fontSize = 1f
    if (st.fontSize > 200f) st.fontSize = 200f
  }
  private fun font(st: Style, lv: String, parent: Style) {
    for (tk in Css.tokens(lv)) {
      val n = tk.toIntOrNull()
      if (tk == "bold" || tk == "bolder") st.bold = true
      else if (tk == "italic" || tk == "oblique") st.italic = true
      else if (n != null && n in 100..900) st.bold = n >= 600
      else if (tk in sizeKw || tk[0].isDigit() || tk[0] == '.') {
        val parts = tk.split("/")
        fontSize(st, parts[0], parent)
        if (parts.size > 1) lineHeight(st, parts[1])
      }
    }
    st.family = familyOf(lv)
  }
  private fun lineHeight(st: Style, lv: String) {
    if (lv == "normal") { st.lineH = 0f; return }
    val n = lv.toFloatOrNull()
    if (n != null) { st.lineH = n; return }
    val l = Css.len(lv, st.fontSize, vw, vh) ?: return
    st.lineH = if (l.pct) l.v / 100f else l.v / st.fontSize
  }
  private fun flexShort(st: Style, lv: String) {
    if (lv == "none") { st.grow = 0f; st.basisZero = false; st.basis = null; return }
    if (lv == "auto") { st.grow = 1f; st.basisZero = false; st.basis = null; return }
    val nums = ArrayList<Float>()
    var basisTok: String? = null
    var auto = false
    for (t in Css.tokens(lv)) {
      val f = t.toFloatOrNull()
      if (f != null) nums.add(f) else if (t == "auto") auto = true else basisTok = t
    }
    if (nums.isNotEmpty()) st.grow = nums[0]
    val bt = basisTok
    if (bt != null) {
      if (bt == "0%" || bt == "0px") { st.basisZero = true; st.basis = null }
      else { st.basisZero = false; st.basis = Css.len(bt, st.fontSize, vw, vh) }
    } else if (auto) { st.basisZero = false; st.basis = null }
    else if (nums.isNotEmpty()) { st.basisZero = true; st.basis = null }
  }
  private fun gridCols(st: Style, lv: String) {
    st.gridWeights = null
    st.gridMin = 0f
    if (lv == "none") return
    val tks = Css.tokens(lv)
    if (tks.size == 1 && tks[0].startsWith("repeat(") && tks[0].endsWith(")")) {
      val inner = tks[0].substring(7, tks[0].length - 1)
      val parts = Css.splitTop(inner, ',')
      if (parts.size >= 2) {
        val n = parts[0].trim().toIntOrNull()
        if (n != null) st.gridWeights = List(Math.max(1, Math.min(n, 12))) { 1f }
        else {
          val pat = parts.subList(1, parts.size).joinToString(",")
          st.gridMin = pxRe.find(pat)?.groupValues?.get(1)?.toFloatOrNull() ?: 200f
        }
      }
      return
    }
    val ws = ArrayList<Float>()
    for (t in tks) ws.add(if (t.endsWith("fr")) (t.dropLast(2).toFloatOrNull() ?: 1f) else 1f)
    if (ws.isNotEmpty()) st.gridWeights = ws.take(12)
  }
  private fun applyProp(st: Style, p: String, v: String, parent: Style) {
    val lv = v.trim().lowercase()
    if (lv.isEmpty() || lv == "inherit" || lv == "initial" || lv == "unset" || lv == "revert") return
    val em = st.fontSize
    when (p) {
      "display" -> st.display = displayOf(lv)
      "visibility" -> st.visHidden = (lv == "hidden" || lv == "collapse")
      "opacity" -> st.zeroOpacity = (lv.toFloatOrNull() == 0f)
      "color" -> { val c = Css.color(lv); if (c != null && c != Css.CUR) st.color = c }
      "background-color" -> { val c = Css.color(lv); if (c != null && c != Css.CUR) st.bg = c }
      "background" -> background(st, lv)
      "background-image" -> { if (st.bg == 0) { val g = gradientColor(lv); if (g != null) st.bg = g } }
      "font-size" -> fontSize(st, lv, parent)
      "font" -> font(st, lv, parent)
      "font-weight" -> st.bold = (lv == "bold" || lv == "bolder" || (lv.toIntOrNull() ?: 0) >= 600)
      "font-style" -> st.italic = (lv.startsWith("italic") || lv.startsWith("oblique"))
      "font-family" -> st.family = familyOf(lv)
      "text-decoration", "text-decoration-line" -> {
        if (lv.contains("none")) { st.underline = false; st.strike = false }
        else {
          if (lv.contains("underline")) st.underline = true
          if (lv.contains("line-through")) st.strike = true
        }
      }
      "text-align" -> st.align = when (lv) { "center", "-webkit-center" -> 1; "right", "end" -> 2; else -> 0 }
      "line-height" -> lineHeight(st, lv)
      "white-space" -> st.pre = (lv.startsWith("pre") || lv == "break-spaces")
      "text-transform" -> st.transform = when (lv) { "uppercase" -> 1; "lowercase" -> 2; "capitalize" -> 3; else -> 0 }
      "list-style", "list-style-type" -> st.listNone = lv.contains("none")
      "margin" -> { val s = sides(Css.tokens(lv)); for (i in 0..3) if (s[i].isNotEmpty()) setMargin(st, i, s[i]) }
      "margin-top" -> setMargin(st, 0, lv)
      "margin-right" -> setMargin(st, 1, lv)
      "margin-bottom" -> setMargin(st, 2, lv)
      "margin-left" -> setMargin(st, 3, lv)
      "padding" -> { val s = sides(Css.tokens(lv)); for (i in 0..3) if (s[i].isNotEmpty()) setPad(st, i, s[i]) }
      "padding-top" -> setPad(st, 0, lv)
      "padding-right" -> setPad(st, 1, lv)
      "padding-bottom" -> setPad(st, 2, lv)
      "padding-left" -> setPad(st, 3, lv)
      "border" -> borderParse(st, intArrayOf(0, 1, 2, 3), lv)
      "border-width" -> {
        val s = sides(Css.tokens(lv))
        for (i in 0..3) { val w = borderWidthOf(s[i], em); if (w != null) st.borderW[i] = w }
      }
      "border-color" -> {
        val s = sides(Css.tokens(lv))
        for (i in 0..3) { val c = Css.color(s[i]); if (c != null) st.borderC[i] = c }
      }
      "border-style" -> { if (lv == "none" || lv == "hidden") for (i in 0..3) st.borderW[i] = 0f }
      "border-radius" -> { val t = Css.tokens(lv); if (t.isNotEmpty()) st.radius = Css.len(t[0], em, vw, vh) }
      "width" -> st.width = Css.len(lv, em, vw, vh)
      "min-width" -> st.minW = Css.len(lv, em, vw, vh)
      "max-width" -> st.maxW = Css.len(lv, em, vw, vh)
      "height" -> st.height = Css.len(lv, em, vw, vh)
      "min-height" -> st.minH = Css.len(lv, em, vw, vh)
      "box-sizing" -> st.borderBox = (lv == "border-box")
      "flex-direction" -> st.column = lv.startsWith("column")
      "flex-wrap" -> st.wrap = lv.startsWith("wrap")
      "flex-flow" -> { for (t in Css.tokens(lv)) { if (t.startsWith("column")) st.column = true else if (t.startsWith("row")) st.column = false else if (t.startsWith("wrap")) st.wrap = true } }
      "justify-content" -> st.justify = when (lv) {
        "center" -> 1
        "flex-end", "end", "right" -> 2
        "space-between" -> 3
        "space-around" -> 4
        "space-evenly" -> 5
        else -> 0
      }
      "align-items" -> st.alignItems = when (lv) {
        "center" -> 1
        "flex-end", "end" -> 2
        "flex-start", "start", "baseline" -> 3
        else -> 0
      }
      "gap", "grid-gap" -> {
        val t = Css.tokens(lv)
        val a = Css.len(t[0], em, vw, vh)
        val b = if (t.size > 1) Css.len(t[1], em, vw, vh) else a
        if (a != null) st.rowGap = a.v
        if (b != null) st.colGap = b.v
      }
      "row-gap", "grid-row-gap" -> { val a = Css.len(lv, em, vw, vh); if (a != null) st.rowGap = a.v }
      "column-gap", "grid-column-gap" -> { val a = Css.len(lv, em, vw, vh); if (a != null) st.colGap = a.v }
      "flex" -> flexShort(st, lv)
      "flex-grow" -> st.grow = lv.toFloatOrNull() ?: st.grow
      "flex-basis" -> {
        if (lv == "0" || lv == "0%" || lv == "0px") { st.basisZero = true; st.basis = null }
        else { st.basisZero = false; st.basis = Css.len(lv, em, vw, vh) }
      }
      "grid-template-columns" -> gridCols(st, lv)
      else -> {
        val m = borderSideRe.matchEntire(p)
        if (m != null) {
          val i = sideIndex(m.groupValues[1])
          when (m.groupValues[2]) {
            "" -> borderParse(st, intArrayOf(i), lv)
            "-width" -> { val w = borderWidthOf(lv, em); if (w != null) st.borderW[i] = w }
            "-color" -> { val c = Css.color(lv); if (c != null) st.borderC[i] = c }
            "-style" -> { if (lv == "none" || lv == "hidden") st.borderW[i] = 0f }
          }
        }
      }
    }
  }
}
