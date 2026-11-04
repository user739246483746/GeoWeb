package dev.motor.engine

import android.graphics.BitmapFactory
import android.util.Base64
import java.util.concurrent.atomic.AtomicLong

object Images {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_PIXELS = 25_000_000L
    private const val BUDGET = 96L * 1024 * 1024
    private val dataRe = Regex("^data:image/(png|jpeg|jpg|gif|webp);base64,", RegexOption.IGNORE_CASE)

    fun load(box: ImageBox, call: Call, maxDim: Int, used: AtomicLong): Boolean {
        val url = box.url ?: return false
        val bytes: ByteArray = if (url.startsWith("data:")) {
            val m = dataRe.find(url) ?: return false
            try { Base64.decode(url.substring(m.range.last + 1), Base64.DEFAULT) } catch (e: IllegalArgumentException) { return false }
        } else {
            Http.fetch(url, call, MAX_BYTES, "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8", Http.IMAGE).bytes
        }
        if (bytes.size > MAX_BYTES) return false

        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        val w = o.outWidth
        val h = o.outHeight
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) return false
        var s = 1
        while (w / s > maxDim || h / s > 4096) s *= 2

        val o2 = BitmapFactory.Options()
        o2.inSampleSize = s
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2) ?: return false
        val size = bmp.allocationByteCount.toLong()
        if (used.addAndGet(size) > BUDGET) {
            used.addAndGet(-size)
            bmp.recycle()
            return false
        }
        box.natW = w.toFloat()
        box.natH = h.toFloat()
        box.bmp = bmp
        return true
    }
}
