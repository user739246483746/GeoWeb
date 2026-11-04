package dev.motor.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.View

class RenderView(ctx: Context) : View(ctx) {
    var onLink: ((String) -> Unit)? = null
    private var root: BlockBox? = null
    private var bg = Color.WHITE
    private val d = resources.displayMetrics.density

    fun show(l: PageLayout) {
        root = l.root
        bg = if (Color.alpha(l.bg) == 255) l.bg else Color.WHITE
        requestLayout()
        invalidate()
    }

    fun relayout() {
        requestLayout()
        invalidate()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val r = root
        if (r == null) { setMeasuredDimension(w, 0); return }
        r.reset()
        val cssW = w / d
        r.layout(cssW, cssW)
        r.x = 0f
        r.y = 0f
        setMeasuredDimension(w, Math.ceil((r.h * d).toDouble()).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)
        val r = root ?: return
        canvas.save()
        canvas.scale(d, d)
        r.draw(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val href = root?.hit(e.x / d, e.y / d)
                if (href != null) onLink?.invoke(href)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()
}
