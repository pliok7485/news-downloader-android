package com.pliok.newsdownloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI

/**
 * 抓取 + 清洗邏輯（由 news_scraper_gui.py 移植）
 *
 * 流程：抓 HTML → 找標題 → 找內文區塊 → 收集段落 → 過濾廣告雜訊 → 去重
 *       → 存 .txt → 有 og:image 就下載封面轉 JPEG
 */
internal object NewsScraper {

    fun normalizeUrl(url: String): String {
        val t = url.trim()
        if (t.isEmpty()) return t
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    fun scrape(context: Context, rawUrl: String, proxy: String?, log: (String) -> Unit): Boolean {
        val url = normalizeUrl(rawUrl)
        try {
            if (!proxy.isNullOrEmpty()) log("🌐 使用代理：$proxy")
            log("正在嘗試抓取：$url")

            val fetcher = HttpFetcher(proxy)
            fetcher.warmup(url)
            val res = fetcher.get(url)
            val doc = Jsoup.parse(ByteArrayInputStream(res.bytes), null, res.finalUrl)

            val title = extractTitle(doc, url)
            var content = extractContent(doc, url)
            if (content.length < 100) {
                val alt = fallbackExtract(doc)
                if (alt.length > content.length) content = alt
            }

            if (content.isEmpty()) {
                log("❌ 錯誤：內文抓取為空！請檢查網站是否更改結構。")
                return false
            }

            log("📝 標題：$title")
            log("📄 內文長度：${content.length} 字")

            val safeTitle = safeFileName(title)
            val textLabel = Storage.saveText(context, "$safeTitle.txt", "$title\n\n$content")
            log("✅ 文字儲存完成：$textLabel")

            val imgUrl = findImageUrl(doc, res.finalUrl)
            if (imgUrl != null) {
                try {
                    val img = fetcher.get(imgUrl, referer = url)
                    val isJpeg = img.contentType.contains("jpeg", true) || img.contentType.contains("jpg", true)
                    val bytes = if (isJpeg) img.bytes else toJpeg(img.bytes)
                    if (bytes == null) {
                        log("⚠️ 圖片格式無法轉換（略過）")
                    } else {
                        val imgLabel = Storage.saveImage(context, "$safeTitle.jpg", bytes)
                        log("🖼️ 圖片儲存完成：$imgLabel")
                    }
                } catch (e: Exception) {
                    log("⚠️ 圖片下載失敗（略過）：${e.message}")
                }
            } else {
                log("ℹ️ 未找到封面圖片，略過圖片下載。")
            }

            return true
        } catch (e: Exception) {
            log("❌ 發生錯誤：${e.javaClass.simpleName}：${e.message ?: "無訊息"}")
            return false
        }
    }

    // ---------------- 標題 ----------------

    private fun extractTitle(doc: Document, url: String): String {
        val cfg = SiteConfigs.forDomain(hostOf(url))
        if (cfg != null) {
            for (selector in cfg.title) {
                val text = safeSelectFirst(doc, selector)?.text()?.trim()
                if (!text.isNullOrEmpty() && text != "...") return text.replace(WHITESPACE, " ")
            }
        }

        val og = safeSelectFirst(doc, "meta[property=\"og:title\"]")?.attr("content")?.trim()
        if (og != null && og.isNotEmpty()) {
            var t: String = og
            for (sep in listOf(" | ", " - ", " – ")) {
                if (t.contains(sep)) t = t.substringBefore(sep).trim()
            }
            if (t.isNotEmpty()) return t
        }

        for (h1 in doc.select("h1")) {
            val text = h1.text().trim()
            if (text.isNotEmpty() && text != "...") return text.replace(WHITESPACE, " ")
        }

        val pageTitle = doc.title().trim()
        if (pageTitle.isNotEmpty()) {
            return pageTitle.substringBefore(" - ").substringBefore(" | ").trim().replace(WHITESPACE, " ")
        }
        return "無標題"
    }

    // ---------------- 內文 ----------------

    private fun extractContent(doc: Document, url: String): String {
        // 移除明顯不是正文的區塊，並把 <br> 轉成換行
        doc.select("script,style,iframe,footer,header,nav,aside,button,noscript,figure").remove()
        doc.select("br").forEach { it.replaceWith(TextNode("\n")) }

        val root = findContentRoot(doc, hostOf(url))

        // 1) 逐個 p / h2 / h3 / li 收集段落
        val paragraphs = ArrayList<String>()
        for (elem in root.select("p,h2,h3,li")) {
            if (elem.classNames().any { cls -> cls.lowercase().contains("ad") }) continue
            var text = elem.wholeText().trim()
            if (text.length < 10) continue
            if (SiteConfigs.isJunk(text)) continue
            if (elem.tagName() == "li") text = "  $text"
            if (!paragraphs.contains(text)) paragraphs.add(text)
        }

        // 2) 用整區純文字補漏（有些站台只用 div + <br> 排版）
        val extra = ArrayList<String>()
        for (rawLine in root.wholeText().split("\n")) {
            val line = rawLine.trim()
            if (line.length < 5) continue
            if (SiteConfigs.isJunk(line)) continue
            if (!extra.contains(line) && !paragraphs.contains(line)) extra.add(line)
        }

        // 3) 合併並去重（前 30 字相同、或一方包含另一方）
        val combined = ArrayList(paragraphs)
        for (p in extra) {
            val prefix = p.take(30)
            var duplicated = false
            for (existing in combined) {
                if (existing.take(30) == prefix) {
                    duplicated = true
                    break
                }
                if (p.length > 50 && existing.length > 50 &&
                    (p.contains(existing) || existing.contains(p))
                ) {
                    duplicated = true
                    break
                }
            }
            if (!duplicated) combined.add(p)
        }

        val mergedLen = combined.sumOf { it.length }
        val baseLen = paragraphs.sumOf { it.length }
        val result = if (mergedLen > baseLen * 1.2) combined else paragraphs
        return result.joinToString("\n\n")
    }

    private fun findContentRoot(doc: Document, domain: String): Element {
        val cfg = SiteConfigs.forDomain(domain)
        if (cfg != null) {
            for (selector in cfg.content) {
                val el = safeSelectFirst(doc, selector)
                if (el != null) return el
            }
        }
        for (selector in SiteConfigs.GENERIC_CONTENT_SELECTORS) {
            val el = safeSelectFirst(doc, selector)
            if (el != null) return el
        }
        return doc.body() ?: doc
    }

    /** 通用後援：挑「p 標籤文字總量最多」的容器（近似 readability） */
    private fun fallbackExtract(doc: Document): String {
        var best: Element? = null
        var bestScore = 0
        for (el in doc.select("article,main,div,section")) {
            val ps = el.select("p")
            if (ps.size < 2) continue
            var score = 0
            for (p in ps) score += p.wholeText().trim().length
            if (score > bestScore) {
                bestScore = score
                best = el
            }
        }
        val root = best ?: return ""
        if (bestScore < 100) return ""
        val out = ArrayList<String>()
        for (elem in root.select("p,h2,h3")) {
            val text = elem.wholeText().trim()
            if (text.length < 10) continue
            if (SiteConfigs.isJunk(text)) continue
            if (!out.contains(text)) out.add(text)
        }
        return out.joinToString("\n\n")
    }

    // ---------------- 圖片 / 檔名 ----------------

    private fun findImageUrl(doc: Document, baseUrl: String): String? {
        val raw = safeSelectFirst(doc, "meta[property=\"og:image\"]")?.attr("content")?.trim()
        if (raw == null || raw.isEmpty()) return null
        return resolveUrl(baseUrl, raw)
    }

    private fun resolveUrl(base: String, link: String): String? = try {
        URI(base).resolve(link).toString()
    } catch (e: Exception) {
        null
    }

    private fun toJpeg(bytes: ByteArray): ByteArray? {
        val bitmap: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun safeFileName(title: String): String {
        val cleaned = title.filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }.trim()
        val name = cleaned.take(50)
        return if (name.isEmpty()) "article_${System.currentTimeMillis() / 1000}" else name
    }

    private fun hostOf(url: String): String = try {
        URI(url).host?.lowercase() ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun safeSelectFirst(doc: Document, selector: String): Element? = try {
        doc.selectFirst(selector)
    } catch (e: Exception) {
        null
    }

    private val WHITESPACE = Regex("\\s+")
}
