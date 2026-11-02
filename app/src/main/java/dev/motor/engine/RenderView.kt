package dev.motor.engine

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/** Desenha os blocos direto na Canvas (sem WebView). Detecta toques em links. */
class RenderView(ctx: Context) : View(ctx) {
    var onLink: ((String) -> Unit)? = null
    private var blocks: List<Block> = emptyList()
    private var layouts: List<StaticLayout> = emptyList()
    private var tops: List<Int> = emptyList()
    private val pad = dp(16)
    private val gap = dp(10)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    fun show(b: List<Block>) {
        blocks = b
        requestLayout()
        invalidate()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val ls = ArrayList<StaticLayout>()
        val ts = ArrayList<Int>()
        var y = pad
        for (b in blocks) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
            p.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, b.sizeSp, resources.displayMetrics)
            p.color = Color.BLACK
            val l = StaticLayout.Builder.obtain(b.text, 0, b.text.length, p, maxOf(1, w - 2 * pad)).build()
            ls.add(l)
            ts.add(y)
            y += l.height + gap
        }
        layouts = ls
        tops = ts
        setMeasuredDimension(w, y + pad)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        for (i in layouts.indices) {
            canvas.save()
            canvas.translate(pad.toFloat(), tops[i].toFloat())
            layouts[i].draw(canvas)
            canvas.restore()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                hit(e.x, e.y)?.let { onLink?.invoke(it) }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun hit(x: Float, y: Float): String? {
        for (i in layouts.indices) {
            val top = tops[i]
            val l = layouts[i]
            if (y >= top && y <= top + l.height) {
                val line = l.getLineForVertical((y - top).toInt())
                val off = l.getOffsetForHorizontal(line, x - pad)
                val sp = blocks[i].text as? Spanned ?: return null
                return sp.getSpans(off, off, URLSpan::class.java).firstOrNull()?.url
            }
        }
        return null
    }
}
