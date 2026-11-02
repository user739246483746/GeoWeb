package dev.motor.engine

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

object Http {
    class Result(val url: String, val body: String)

    private val charsetRe = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)

    fun get(start: String): Result {
        var u = start
        for (hop in 0..8) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "MotorBrowser/0.1 (Android)")
            c.setRequestProperty("Accept", "text/html,*/*;q=0.8")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                if (loc != null) {
                    u = URL(URL(u), loc).toString()
                    c.disconnect()
                    continue
                }
            }
            val ct = c.contentType ?: ""
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val body = if (ct.isNotEmpty() && !ct.contains("text") && !ct.contains("xml")) {
                "[Conteúdo não suportado: $ct]"
            } else {
                val cs = charsetRe.find(ct)?.groupValues?.get(1)
                val charset = try { Charset.forName(cs ?: "UTF-8") } catch (e: Exception) { Charsets.UTF_8 }
                stream?.use { it.readBytes().toString(charset) } ?: ""
            }
            return Result(u, body)
        }
        throw IOException("Redirecionamentos demais")
    }
}
