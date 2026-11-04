package dev.motor.engine
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.URLSpan
object Gfx {
  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  fun radius(st: Style, w: Float, h: Float): Float {
    val r = st.radius ?: return 0f
    val v = if (r.pct) r.v / 100f * Math.min(w, h) else r.v
    return Math.max(0f, Math.min(v, Math.min(w, h) / 2f))
  }
}
abstract class Box {
  var x = 0f
  var y = 0f
  var w = 0f
  var h = 0f
  var ml = 0f
  var mr = 0f
  var mt = 0f
  var mb = 0f
  abstract fun layout(availW: Float, fixedW: Float)
  abstract fun draw(c: Canvas)
  open fun hit(hx: Float, hy: Float): String? = null
  open fun maxContent(): Float = 0f
  open fun margH(availW: Float): Float = 0f
  open fun extraW(availW: Float): Float = 0f
  open fun stretchTo(newH: Float) { }
  open fun reset() { }
  protected fun lpx(l: Len?, base: Float): Float = if (l == null) 0f else if (l.pct) l.v * base / 100f else l.v
  protected fun margins(st: Style, availW: Float) {
    mt = Math.max(0f, lpx(st.margin[0], availW))
    mr = Math.max(0f, lpx(st.margin[1], availW))
    mb = Math.max(0f, lpx(st.margin[2], availW))
    ml = Math.max(0f, lpx(st.margin[3], availW))
  }
}
class TextBox(val text: CharSequence, val st: Style) : Box() {
  private var lay: StaticLayout? = null
  private var intrinsic = -1f
  private fun build(width: Int): StaticLayout {
    val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
    p.textSize = st.fontSize
    p.color = st.color
    val b = StaticLayout.Builder.obtain(text, 0, text.length, p, width)
    b.setIncludePad(false)
    b.setAlignment(
      when (st.align) {
        1 -> Layout.Alignment.ALIGN_CENTER
        2 -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
      }
    )
    if (st.lineH > 0f) b.setLineSpacing(0f, Math.min(3f, Math.max(0.7f, st.lineH / 1.17f)))
    return b.build()
  }
  override fun layout(availW: Float, fixedW: Float) {
    val width = if (fixedW >= 0f) fixedW else availW
    val l = build(Math.max(1, width.toInt()))
    lay = l
    w = width
    h = l.height.toFloat()
    ml = 0f; mr = 0f; mt = 0f; mb = 0f
  }
  override fun maxContent(): Float {
    if (intrinsic >= 0f) return intrinsic
    val l = build(100000)
    var m = 0f
    for (i in 0 until l.lineCount) m = Math.max(m, l.getLineWidth(i))
    intrinsic = Math.ceil(m.toDouble()).toFloat() + 1f
    return intrinsic
  }
  override fun draw(c: Canvas) { lay?.draw(c) }
  override fun hit(hx: Float, hy: Float): String? {
    val l = lay ?: return null
    if (hy < 0f || hy > l.height) return null
    val line = l.getLineForVertical(hy.toInt())
    if (hx < l.getLineLeft(line) - 4f || hx > l.getLineRight(line) + 4f) return null
    val off = l.getOffsetForHorizontal(line, hx)
    val sp = text as? Spanned ?: return null
    val spans = sp.getSpans(off, off, URLSpan::class.java)
    return if (spans.isNotEmpty()) spans[0].url else null
  }
}
class ImageBox(val st: Style, val url: String?, val alt: String, val attrW: Float, val attrH: Float) : Box() {
  @Volatile var bmp: Bitmap? = null
  @Volatile var natW = 0f
  @Volatile var natH = 0f
  var href: String? = null
  private fun dims(availW: Float): FloatArray {
    val sw = st.width
    val sh = st.height
    val cw = if (sw != null) lpx(sw, availW) else 0f
    val chh = if (sh != null && !sh.pct) sh.v else 0f
    val nw = if (natW > 0f) natW else attrW
    val nh = if (natH > 0f) natH else attrH
    val ratio = if (nw > 0f && nh > 0f) nh / nw else 0f
    var wv = if (cw > 0f) cw else if (chh > 0f && ratio > 0f) chh / ratio else if (nw > 0f) nw else Math.min(availW, 100f)
    var hv = if (chh > 0f) chh else if (ratio > 0f) wv * ratio else if (bmp == null) 60f else wv
    var limit = availW - ml - mr
    val mx = st.maxW
    if (mx != null) limit = Math.min(limit, lpx(mx, availW))
    if (limit > 0f && wv > limit) { hv *= limit / wv; wv = limit }
    return floatArrayOf(wv, hv)
  }
  override fun margH(availW: Float): Float = lpx(st.margin[1], availW) + lpx(st.margin[3], availW)
  override fun maxContent(): Float = dims(100000f)[0]
  override fun layout(availW: Float, fixedW: Float) {
    margins(st, availW)
    val d = dims(availW)
    if (fixedW >= 0f) {
      h = if (d[0] > 0f) d[1] * fixedW / d[0] else d[1]
      w = fixedW
    } else {
      w = d[0]; h = d[1]
      val free = availW - ml - mr - w
      if (free > 0f) {
        if (st.marginAuto[3] && st.marginAuto[1]) { ml = free / 2f; mr = free / 2f }
        else if (st.marginAuto[3]) ml = free
      }
    }
  }
  override fun draw(c: Canvas) {
    if (w < 3f || h < 3f) return
    val p = Gfx.paint
    val b = bmp
    val rad = Gfx.radius(st, w, h)
    if (b != null) {
      c.save()
      if (rad > 0f) {
        val path = Path()
        path.addRoundRect(RectF(0f, 0f, w, h), rad, rad, Path.Direction.CW)
        c.clipPath(path)
      }
      p.style = Paint.Style.FILL
      p.color = Color.WHITE
      p.isFilterBitmap = true
      c.drawBitmap(b, null, RectF(0f, 0f, w, h), p)
      c.restore()
    } else {
      p.style = Paint.Style.FILL
      p.color = 0xFFEEEEEE.toInt()
      c.drawRect(0f, 0f, w, h, p)
      if (alt.isNotEmpty()) {
        p.color = 0xFF777777.toInt()
        p.textSize = 12f
        c.save()
        c.clipRect(0f, 0f, w, h)
        c.drawText(alt, 4f, 16f, p)
        c.restore()
      }
    }
  }
  override fun hit(hx: Float, hy: Float): String? = href
}
