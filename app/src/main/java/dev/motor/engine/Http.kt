package dev.motor.engine

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection

/** Permite cancelar uma navegação em andamento (fecha a conexão na hora). */
class Call {
    @Volatile var cancelled = false
    @Volatile private var conn: HttpsURLConnection? = null
    fun attach(c: HttpsURLConnection) { conn = c; if (cancelled) c.disconnect() }
    fun cancel() { cancelled = true; conn?.disconnect() }
}

object Http {
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_REDIRECTS = 5
    private const val TOTAL_NS = 30_000_000_000L // 30 s para a cadeia inteira

    class Result(val url: String, val body: String)

    private val charsetRe = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) MotorBrowser/0.2 Mobile Safari/537.36"

    private fun alive(call: Call, t0: Long) {
        if (call.cancelled) throw IOException("Cancelado")
        if (System.nanoTime() - t0 > TOTAL_NS) throw IOException("Tempo total excedido (30 s)")
    }

    fun get(start: String, call: Call): Result {
        val t0 = System.nanoTime()
        var u = UrlGuard.check(start)
        var hops = 0
        while (true) {
            alive(call, t0)
            UrlGuard.assertPublicHost(URI(u).host)
            val c = URL(u).openConnection() as HttpsURLConnection   // validação de certificado/host: padrão do Android
            call.attach(c)
            try {
                c.instanceFollowRedirects = false
                c.useCaches = false
                c.connectTimeout = 8000
                c.readTimeout = 10000
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.5")
                c.setRequestProperty("DNT", "1")
                c.setRequestProperty("Sec-GPC", "1")

                val code = c.responseCode
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    val loc = c.getHeaderField("Location")
                    if (loc != null) {
                        if (++hops > MAX_REDIRECTS) throw IOException("Redirecionamentos demais")
                        // cada salto passa pela mesma validação; http:// vira https:// (nunca cai para texto puro)
                        u = UrlGuard.check(URI(u).resolve(loc.trim()).toString())
                        continue
                    }
                }

                val ct = c.contentType ?: ""
                val ctl = ct.lowercase()
                if (ctl.isNotEmpty() && !ctl.contains("text/") && !ctl.contains("xml")) {
                    return Result(u, "[Conteúdo não suportado: ${Sanitize.text(ct)}]")
                }
                val len = c.contentLengthLong
                if (len > MAX_BYTES) throw IOException("Página grande demais (${len / 1024} KB; limite 5 MB)")

                val stream = if (code >= 400) c.errorStream else c.inputStream
                val bytes = stream?.use { readLimited(it, call, t0) } ?: ByteArray(0)
                val cs = charsetRe.find(ct)?.groupValues?.get(1)
                val charset = try { Charset.forName(cs ?: "UTF-8") } catch (e: Exception) { Charsets.UTF_8 }
                return Result(u, String(bytes, charset))
            } finally {
                c.disconnect()
            }
        }
    }

    // limite aplicado sobre os bytes já descomprimidos (protege contra "zip bomb")
    private fun readLimited(s: InputStream, call: Call, t0: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            alive(call, t0)
            val n = s.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) throw IOException("Resposta passou do limite de 5 MB")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
