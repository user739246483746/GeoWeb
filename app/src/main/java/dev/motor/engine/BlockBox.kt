package dev.motor.engine
import android.graphics.Canvas
import android.graphics.Paint
class BlockBox(val st: Style) : Box() {
  val children = ArrayList<Box>()
  private var mc = -1f
  override fun margH(availW: Float): Float =
    Math.max(0f, lpx(st.margin[1], availW)) + Math.max(0f, lpx(st.margin[3], availW))
  override fun extraW(availW: Float): Float =
    Math.max(0f, lpx(st.padding[1], availW)) + Math.max(0f, lpx(st.padding[3], availW)) + st.borderW[1] + st.borderW[3]
  override fun reset() {
    mc = -1f
    for (c in children) c.reset()
  }
  override fun maxContent(): Float {
    if (mc >= 0f) return mc
    val extra = extraW(0f)
    val cssW = st.width
    val res: Float
    if (cssW != null && !cssW.pct) {
      res = if (st.borderBox) cssW.v else cssW.v + extra
    } else {
      var inner = 0f
      if (st.display == D.FLEX && !st.column) {
        for (c in children) inner += c.maxContent() + c.margH(0f)
        if (children.size > 1) inner += st.colGap * (children.size - 1)
      } else if (st.display == D.GRID) {
        var m = 0f
        for (c in children) m = Math.max(m, c.maxContent() + c.margH(0f))
        inner = m * Math.max(1, st.gridWeights?.size ?: 2)
      } else {
        for (c in children) inner = Math.max(inner, c.maxContent() + c.margH(0f))
      }
      res = inner + extra
    }
    mc = res
    return res
  }
  override fun stretchTo(newH: Float) { if (newH > h) h = newH }
  override fun layout(availW: Float, fixedW: Float) {
    margins(st, availW)
    val padL = Math.max(0f, lpx(st.padding[3], availW))
    val padR = Math.max(0f, lpx(st.padding[1], availW))
    val padT = Math.max(0f, lpx(st.padding[0], availW))
    val padB = Math.max(0f, lpx(st.padding[2], availW))
    val bT = st.borderW[0]
    val bR = st.borderW[1]
    val bB = st.borderW[2]
    val bL = st.borderW[3]
    val extra = padL + padR + bL + bR
    var bw: Float
    if (fixedW >= 0f) {
      bw = fixedW
    } else {
      val cssW = st.width
      bw = if (cssW != null) {
        val v = lpx(cssW, availW)
        if (st.borderBox) v else v + extra
      } else availW - ml - mr
      val mx = st.maxW
      if (mx != null) {
        val v = lpx(mx, availW)
        val lim = if (st.borderBox) v else v + extra
        if (bw > lim) bw = lim
      }
      val mn = st.minW
      if (mn != null) {
        val v = lpx(mn, availW)
        val lim = if (st.borderBox) v else v + extra
        if (bw < lim) bw = lim
      }
      val free = availW - ml - mr - bw
      if (free > 0f) {
        if (st.marginAuto[3] && st.marginAuto[1]) { ml = free / 2f; mr = free / 2f }
        else if (st.marginAuto[3]) ml = free
        else if (st.marginAuto[1]) mr = free
      }
    }
    if (bw < extra) bw = extra
    w = bw
    val innerW = Math.max(1f, bw - extra)
    val used = layoutChildren(innerW, bL + padL, bT + padT)
    var hh = bT + padT + used + padB + bB
    var minHv = 0f
    val hs = st.height
    val mh = st.minH
    if (hs != null && !hs.pct) minHv = Math.max(minHv, hs.v)
    if (mh != null && !mh.pct) minHv = Math.max(minHv, mh.v)
    if (minHv > 0f) {
      val boxMin = if (st.borderBox) minHv else minHv + padT + padB + bT + bB
      if (hh < boxMin) hh = boxMin
    }
    h = hh
  }
  private fun layoutChildren(innerW: Float, x0: Float, y0: Float): Float {
    if (children.isEmpty()) return 0f
    return when (st.display) {
      D.FLEX -> if (st.column) stack(innerW, x0, y0, st.rowGap, st.alignItems == 1) else flexRow(innerW, x0, y0)
      D.GRID -> grid(innerW, x0, y0)
      else -> stack(innerW, x0, y0, 0f, false)
    }
  }
  private fun stack(innerW: Float, x0: Float, y0: Float, gap: Float, center: Boolean): Float {
    var y = 0f
    var prevMb = 0f
    var first = true
    for (ch in children) {
      if (center) {
        val mhh = ch.margH(innerW)
        ch.layout(innerW, Math.max(1f, Math.min(innerW, ch.maxContent() + mhh) - mhh))
      } else {
        ch.layout(innerW, -1f)
      }
      y += if (first) ch.mt else Math.max(prevMb, ch.mt) + gap
      val cx = if (center) Math.max(0f, (innerW - ch.ml - ch.mr - ch.w) / 2f) else 0f
      ch.x = x0 + ch.ml + cx
      ch.y = y0 + y
      y += ch.h
      prevMb = ch.mb
      first = false
    }
    return y + prevMb
  }
  private fun flexRow(innerW: Float, x0: Float, y0: Float): Float {
    val n = children.size
    val basis = FloatArray(n)
    val grow = FloatArray(n)
    val outerM = FloatArray(n)
    val autoCnt = IntArray(n)
    for (i in 0 until n) {
      val c = children[i]
      outerM[i] = c.margH(innerW)
      if (c is BlockBox) {
        val cs = c.st
        val ex = c.extraW(innerW)
        grow[i] = cs.grow
        if (cs.marginAuto[1]) autoCnt[i]++
        if (cs.marginAuto[3]) autoCnt[i]++
        basis[i] = if (cs.basis != null) {
          val v = lpx(cs.basis, innerW)
          if (cs.borderBox) v else v + ex
        } else if (cs.width != null) {
          val v = lpx(cs.width, innerW)
          if (cs.borderBox) v else v + ex
        } else if (cs.basisZero) ex
        else c.maxContent()
      } else {
        basis[i] = c.maxContent()
      }
    }
    val gapX = st.colGap
    val gapY = st.rowGap
    var y = 0f
    var i = 0
    var firstLine = true
    while (i < n) {
      var j = i
      var sum = 0f
      while (j < n) {
        val add = basis[j] + outerM[j] + (if (j > i) gapX else 0f)
        if (st.wrap && j > i && sum + add > innerW + 0.5f) break
        sum += add
        j++
      }
      val k = j - i
      var free = innerW - sum
      var totalGrow = 0f
      var totalBasis = 0f
      for (q in 0 until k) { totalGrow += grow[i + q]; totalBasis += basis[i + q] }
      val widths = FloatArray(k)
      for (q in 0 until k) {
        var wq = basis[i + q]
        if (free > 0f && totalGrow > 0f) wq += free * grow[i + q] / totalGrow
        else if (free < 0f && totalBasis > 0f) wq = Math.max(children[i + q].extraW(innerW), wq + free * basis[i + q] / totalBasis)
        widths[q] = Math.max(1f, wq)
      }
      if (free > 0f && totalGrow > 0f) free = 0f
      var autoTotal = 0
      for (q in 0 until k) autoTotal += autoCnt[i + q]
      var autoShare = 0f
      if (free > 0f && autoTotal > 0) { autoShare = free / autoTotal; free = 0f }
      for (q in 0 until k) {
        val c = children[i + q]
        c.layout(innerW, widths[q])
        if (autoShare > 0f && c is BlockBox) {
          if (c.st.marginAuto[3]) c.ml = autoShare
          if (c.st.marginAuto[1]) c.mr = autoShare
        }
      }
      var spacing = 0f
      var off = 0f
      if (free > 0f) {
        when (st.justify) {
          1 -> { off = free / 2f }
          2 -> { off = free }
          3 -> { if (k > 1) spacing = free / (k - 1) }
          4 -> { spacing = free / k; off = spacing / 2f }
          5 -> { spacing = free / (k + 1); off = spacing }
        }
      }
      if (!firstLine) y += gapY
      var cx = x0 + off
      var lineH = 0f
      for (q in 0 until k) {
        val c = children[i + q]
        cx += c.ml
        c.x = cx
        cx += c.w + c.mr + gapX + spacing
        lineH = Math.max(lineH, c.mt + c.h + c.mb)
      }
      for (q in 0 until k) {
        val c = children[i + q]
        val spare = lineH - (c.mt + c.h + c.mb)
        when (st.alignItems) {
          1 -> c.y = y0 + y + c.mt + spare / 2f
          2 -> c.y = y0 + y + c.mt + spare
          3 -> c.y = y0 + y + c.mt
          else -> { c.y = y0 + y + c.mt; c.stretchTo(c.h + spare) }
        }
      }
      y += lineH
      firstLine = false
      i = j
    }
    return y
  }
  private fun grid(innerW: Float, x0: Float, y0: Float): Float {
    val gapX = st.colGap
    val gapY = st.rowGap
    var weights: List<Float> = st.gridWeights ?: listOf(1f)
    if (st.gridWeights == null && st.gridMin > 0f) {
      val cols = Math.max(1, Math.min(12, ((innerW + gapX) / (st.gridMin + gapX)).toInt()))
      weights = List(cols) { 1f }
    }
    val cols = weights.size
    var sumW = 0f
    for (wt in weights) sumW += wt
    if (sumW <= 0f) sumW = cols.toFloat()
    val avail = Math.max(1f, innerW - gapX * (cols - 1))
    var y = 0f
    var i = 0
    var firstRow = true
    while (i < children.size) {
      val end = Math.min(children.size, i + cols)
      if (!firstRow) y += gapY
      var rowH = 0f
      var cx = x0
      for (q in i until end) {
        val c = children[q]
        val cw = Math.max(1f, avail * weights[q - i] / sumW)
        c.layout(cw, -1f)
        c.x = cx + c.ml
        cx += cw + gapX
        rowH = Math.max(rowH, c.mt + c.h + c.mb)
      }
      for (q in i until end) {
        val c = children[q]
        c.y = y0 + y + c.mt
        c.stretchTo(rowH - c.mt - c.mb)
      }
      y += rowH
      firstRow = false
      i = end
    }
    return y
  }
  override fun draw(c: Canvas) {
    val p = Gfx.paint
    val r = Gfx.radius(st, w, h)
    if (st.bg != 0) {
      p.style = Paint.Style.FILL
      p.color = st.bg
      if (r > 0f) c.drawRoundRect(0f, 0f, w, h, r, r, p) else c.drawRect(0f, 0f, w, h, p)
    }
    val bw = st.borderW
    if (bw[0] > 0f || bw[1] > 0f || bw[2] > 0f || bw[3] > 0f) drawBorders(c, r)
    val clip = c.clipBounds
    for (ch in children) {
      if (clip != null && (ch.y + ch.h < clip.top || ch.y > clip.bottom)) continue
      c.save()
      c.translate(ch.x, ch.y)
      ch.draw(c)
      c.restore()
    }
  }
  private fun drawBorders(c: Canvas, r: Float) {
    val p = Gfx.paint
    val bw = st.borderW
    val col = IntArray(4) { i -> if (st.borderC[i] == Css.CUR) st.color else st.borderC[i] }
    if (r > 0f && bw[0] > 0f && bw[0] == bw[1] && bw[1] == bw[2] && bw[2] == bw[3] &&
      col[0] == col[1] && col[1] == col[2] && col[2] == col[3]
    ) {
      p.style = Paint.Style.STROKE
      p.strokeWidth = bw[0]
      p.color = col[0]
      val hw = bw[0] / 2f
      c.drawRoundRect(hw, hw, w - hw, h - hw, r, r, p)
      p.style = Paint.Style.FILL
      return
    }
    p.style = Paint.Style.FILL
    if (bw[0] > 0f) { p.color = col[0]; c.drawRect(0f, 0f, w, bw[0], p) }
    if (bw[1] > 0f) { p.color = col[1]; c.drawRect(w - bw[1], 0f, w, h, p) }
    if (bw[2] > 0f) { p.color = col[2]; c.drawRect(0f, h - bw[2], w, h, p) }
    if (bw[3] > 0f) { p.color = col[3]; c.drawRect(0f, 0f, bw[3], h, p) }
  }
  override fun hit(hx: Float, hy: Float): String? {
    for (i in children.indices.reversed()) {
      val ch = children[i]
      if (hx >= ch.x && hx <= ch.x + ch.w && hy >= ch.y && hy <= ch.y + ch.h) {
        val r = ch.hit(hx - ch.x, hy - ch.y)
        if (r != null) return r
      }
    }
    return null
  }
}
