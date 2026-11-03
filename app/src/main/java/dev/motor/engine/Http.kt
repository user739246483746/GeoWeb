package dev.motor.engine

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

class Call {
    @Volatile var cancelled = false
    private val conns: MutableSet<HttpsURLConnection> =
        java.util.Collections.newSetFromMap(ConcurrentHashMap<HttpsURLConnection, Boolean>())

    fun attach(c: HttpsURLConnection) { conns.add(c); if (cancelled) c.disconnect() }
    fun detach(c: HttpsURLConnection) { conns.remove(c) }
    fun cancel() {
        cancelled = true
        for (c in conns) { try { c.disconnect() } catch (e: Exception) { } }
    }
}

object Http {
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_REDIRECTS = 5
    const val TEXT = 0
    const val IMAGE = 1
    private const val TOTAL_NS = 30_000_000_000L

    class Result(val url: String, val body: String)
    class Fetched(val url: String, val bytes: ByteArray, val contentType: String)

    private val charsetRe = Regex("charset=\"?([\\w-]+)", RegexOption.IGNORE_CASE)
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) MotorBrowser/0.3 Mobile Safari/537.36"

    private fun alive(call: Call, t0: Long) {
        if (call.cancelled) throw IOException("Cancelado")
        if (System.nanoTime() - t0 > TOTAL_NS) throw IOException("Tempo total excedido (30 s)")
    }

    fun get(start: String, call: Call): Result {
        val f = fetch(start, call, MAX_BYTES, "text/html,application/xhtml+xml,*/*;q=0.8", TEXT)
        return Result(f.url, decode(f))
    }

    fun decode(f: Fetched): String {
        var cs = charsetRe.find(f.contentType)?.groupValues?.get(1)
        if (cs == null) {
            val head = String(f.bytes, 0, Math.min(f.bytes.size, 2048), Charsets.ISO_8859_1)
            cs = charsetRe.find(head)?.groupValues?.get(1)
        }
        val charset = try { Charset.forName(cs ?: "UTF-8") } catch (e: Exception) { Charsets.UTF_8 }
        return String(f.bytes, charset)
    }

    fun fetch(start: String, call: Call, maxBytes: Int, accept: String, kind: Int): Fetched {
        val t0 = System.nanoTime()
        var u = UrlGuard.check(start)
        var hops = 0
        while (true) {
            alive(call, t0)
            UrlGuard.assertPublicHost(URI(u).host)
            val c = URL(u).openConnection() as HttpsURLConnection
            call.attach(c)
            try {
                c.instanceFollowRedirects = false
                c.useCaches = false
                c.connectTimeout = 8000
                c.readTimeout = 10000
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Accept", accept)
                c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.5")
                c.setRequestProperty("DNT", "1")
                c.setRequestProperty("Sec-GPC", "1")

                val code = c.responseCode
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    val loc = c.getHeaderField("Location")
                    if (loc != null) {
                        if (++hops > MAX_REDIRECTS) throw IOException("Redirecionamentos demais")
                        u = UrlGuard.check(URI(u).resolve(loc.trim()).toString())
                        continue
                    }
                }
                if (code >= 400 && kind != TEXT) throw IOException("HTTP $code")

                val ct = c.contentType ?: ""
                val ctl = ct.lowercase()
                val okType = if (kind == IMAGE) {
                    ctl.startsWith("image/") && !ctl.contains("svg")
                } else {
                    ctl.isEmpty() || ctl.contains("text/") || ctl.contains("xml")
                }
                if (!okType) throw IOException("Tipo de conteúdo não suportado: ${Sanitize.text(ct)}")
                val len = c.contentLengthLong
                if (len > maxBytes) throw IOException("Arquivo grande demais (${len / 1024} KB)")

                val stream = if (code >= 400) c.errorStream else c.inputStream
                val bytes = stream?.use { readLimited(it, call, t0, maxBytes) } ?: ByteArray(0)
                return Fetched(u, bytes, ct)
            } finally {
                call.detach(c)
                c.disconnect()
            }
        }
    }

    private fun readLimited(s: InputStream, call: Call, t0: Long, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            alive(call, t0)
            val n = s.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw IOException("Resposta passou do limite de ${maxBytes / 1024} KB")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
