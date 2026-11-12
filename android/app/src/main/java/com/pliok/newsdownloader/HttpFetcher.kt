package com.pliok.newsdownloader

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.random.Random

/**
 * 極簡 HTTP 抓取器（取代 Python 版的 curl_cffi / requests）。
 *
 * - 使用桌面版 Chrome 的標頭（網站設定是以桌機版 HTML 調校的）
 * - 會記住 Cookie，並支援「先逛首頁預熱」取得反爬 Cookie
 * - 支援 http(s) 與 socks5 代理（Java 的 SOCKS 由代理端解析 DNS，適合 Tor）
 */
internal class HttpFetcher(proxyString: String?) {

    class Result(val bytes: ByteArray, val contentType: String, val finalUrl: String)

    private val proxy: Proxy? = parseProxy(proxyString)
    private val userAgent: String = UA_POOL.random()
    private val cookieJar = HashMap<String, LinkedHashMap<String, String>>()
    private val warmedHosts = HashSet<String>()

    /** 先訪問首頁拿 Cookie（失敗不影響後續） */
    fun warmup(url: String) {
        try {
            val u = URL(url)
            if (!warmedHosts.add(u.host)) return
            get("${u.protocol}://${u.host}/", WARMUP_TIMEOUT)
        } catch (_: Exception) {
        }
    }

    fun get(url: String, timeoutMs: Int = REQUEST_TIMEOUT, referer: String? = null): Result {
        var lastError: Exception? = null
        for (attempt in 0 until RETRIES) {
            try {
                return request(url, timeoutMs, referer)
            } catch (e: Exception) {
                lastError = e
                if (attempt < RETRIES - 1) {
                    Thread.sleep(1500L + Random.nextLong(1500L))
                }
            }
        }
        throw lastError ?: IOException("未知錯誤")
    }

    private fun request(url: String, timeoutMs: Int, referer: String?): Result {
        val u = URL(url)
        val conn = (if (proxy != null) u.openConnection(proxy) else u.openConnection()) as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        )
        conn.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7")
        conn.setRequestProperty("Accept-Encoding", "gzip")
        conn.setRequestProperty("DNT", "1")
        conn.setRequestProperty("Upgrade-Insecure-Requests", "1")
        if (!referer.isNullOrEmpty()) conn.setRequestProperty("Referer", referer)
        cookieJar[u.host]?.takeIf { it.isNotEmpty() }?.let { jar ->
            conn.setRequestProperty("Cookie", jar.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }

        try {
            val code = conn.responseCode
            storeCookies(u.host, conn)
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }
            val encoding = (conn.contentEncoding ?: "").lowercase()
            val stream: InputStream = when {
                encoding.contains("gzip") -> GZIPInputStream(conn.inputStream)
                encoding.contains("deflate") -> InflaterInputStream(conn.inputStream)
                else -> conn.inputStream
            }
            val bytes = stream.use { it.readBytes() }
            val finalUrl = conn.url?.toString() ?: url
            return Result(bytes, conn.contentType ?: "", finalUrl)
        } finally {
            conn.disconnect()
        }
    }

    private fun storeCookies(host: String, conn: HttpURLConnection) {
        try {
            for ((key, values) in conn.headerFields) {
                if (key == null || values == null) continue
                if (!key.equals("Set-Cookie", true)) continue
                val jar = cookieJar.getOrPut(host) { LinkedHashMap() }
                for (c in values) {
                    val first = c.substringBefore(';')
                    val name = first.substringBefore('=', "").trim()
                    val value = first.substringAfter('=', "").trim()
                    if (name.isNotEmpty()) jar[name] = value
                }
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val REQUEST_TIMEOUT = 45000
        const val WARMUP_TIMEOUT = 20000
        private const val RETRIES = 3

        private val UA_POOL = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
        )

        /** 把 "http://host:port" / "socks5://host:port" 轉成 java.net.Proxy */
        fun parseProxy(s: String?): Proxy? {
            if (s.isNullOrBlank()) return null
            var t = s.trim()
            val scheme = if (t.contains("://")) t.substringBefore("://").lowercase() else ""
            if (scheme.isNotEmpty()) t = t.substringAfter("://")
            t = t.substringBefore("/")
            if (t.isEmpty()) return null
            val host = if (t.contains(':')) t.substringBeforeLast(':') else t
            val portText = if (t.contains(':')) t.substringAfterLast(':') else ""
            val isSocks = scheme.startsWith("socks")
            val port = portText.toIntOrNull() ?: if (isSocks) 1080 else 8080
            if (host.isEmpty()) return null
            return try {
                Proxy(
                    if (isSocks) Proxy.Type.SOCKS else Proxy.Type.HTTP,
                    InetSocketAddress.createUnresolved(host, port)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
