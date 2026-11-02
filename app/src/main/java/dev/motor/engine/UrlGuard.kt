package dev.motor.engine

import java.net.InetAddress
import java.net.URI

class UrlException(msg: String) : Exception(msg)

/** Única porta de entrada para URLs: tudo que vira requisição passa por aqui. */
object UrlGuard {
    private const val MAX_LEN = 2048
    private val hostRe = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$")
    private const val UNSAFE = "\"<>\\^`{|}[]"

    /** Aceita só http/https (http é promovido a https). Devolve a URL canônica, sempre https. */
    fun check(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty() || s.length > MAX_LEN) throw UrlException("Endereço vazio ou longo demais")
        if (s.any { it.isISOControl() || it.isWhitespace() }) throw UrlException("Endereço contém caracteres inválidos")
        val u = try { URI(encode(s)) } catch (e: Exception) { throw UrlException("Endereço inválido") }

        val scheme = u.scheme?.lowercase() ?: throw UrlException("Endereço sem esquema")
        if (scheme != "https" && scheme != "http") throw UrlException("Esquema bloqueado: só https é permitido")
        if (u.rawUserInfo != null) throw UrlException("Endereços com usuário/senha são bloqueados")

        val host = u.host?.lowercase() ?: throw UrlException("Host inválido")
        if (host.length > 253 || !hostRe.matches(host)) throw UrlException("Host inválido")

        val port = u.port
        val okPort = port == -1 || port == 443 || port == 8443 || (scheme == "http" && port == 80)
        if (!okPort) throw UrlException("Porta bloqueada: $port")

        val portPart = if (port == 8443) ":8443" else ""
        val path = u.rawPath.let { if (it.isNullOrEmpty()) "/" else it }
        val query = u.rawQuery?.let { "?$it" } ?: ""
        return "https://$host$portPart$path$query"   // fragmento (#) nunca sai do aparelho
    }

    /** Bloqueia destinos em rede local/privada (proteção contra redirecionamento para roteador/intranet). */
    fun assertPublicHost(host: String) {
        val addrs = try { InetAddress.getAllByName(host) } catch (e: Exception) {
            throw UrlException("Não foi possível resolver o endereço")
        }
        for (a in addrs) if (blocked(a)) throw UrlException("Destino em rede local/privada bloqueado")
    }

    private fun blocked(a: InetAddress): Boolean {
        if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress ||
            a.isSiteLocalAddress || a.isMulticastAddress
        ) return true
        val b = a.address
        if (b.size == 4) {
            val b0 = b[0].toInt() and 255
            val b1 = b[1].toInt() and 255
            if (b0 == 0 || b0 >= 240) return true
            if (b0 == 100 && b1 in 64..127) return true      // CGNAT
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return true
            if (b0 == 192 && b1 == 0) return true
        } else if (b.size == 16) {
            if ((b[0].toInt() and 0xFE) == 0xFC) return true // fc00::/7
        }
        return false
    }

    private fun encode(s: String): String {
        val sb = StringBuilder(s.length + 16)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            if (cp in 33..126 && UNSAFE.indexOf(cp.toChar()) < 0) {
                sb.append(cp.toChar())
            } else {
                for (x in String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)) {
                    sb.append('%').append(String.format("%02X", x.toInt() and 0xFF))
                }
            }
        }
        return sb.toString()
    }
}
