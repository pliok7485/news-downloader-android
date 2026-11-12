package com.pliok.newsdownloader

/**
 * 各新聞網站的內文 / 標題 CSS 選擇器。
 * 內容直接沿用桌面版 news_scraper_gui.py 的 SITE_CONFIGS 表。
 */
internal data class SiteCfg(
    val content: List<String>,
    val title: List<String>
)

internal object SiteConfigs {

    val MAP: List<Pair<String, SiteCfg>> = listOf(
        "cna.com.tw" to SiteCfg(listOf("div.paragraph", "article"), listOf("h1.title", "h1")),
        "ltn.com.tw" to SiteCfg(
            listOf("div.text.boxTitle.boxText", "div.breaknews-content", "div.content", "article"),
            listOf("h1.boxTitle", "h1")
        ),
        "udn.com" to SiteCfg(listOf("div.article-content__paragraph", "article"), listOf("h1.article-content__title", "h1")),
        "appledaily.com" to SiteCfg(listOf("div.ndArticle_content", "article"), listOf("h1.ndArticle_title", "h1")),
        "tvbs.com.tw" to SiteCfg(listOf("div.newsdetail-content", "article"), listOf("h1.newsdetail-title", "h1")),
        "ettoday.net" to SiteCfg(listOf("div.story", "article"), listOf("h1.title", "h1")),
        "chinatimes.com" to SiteCfg(listOf("div.article-body", "article"), listOf("h1.article-title", "h1")),
        "setn.com" to SiteCfg(listOf("div.Content", "article"), listOf("h1.news-title", "h1")),
        "ftv.com.tw" to SiteCfg(listOf("div.news-content", "article"), listOf("h1", "h1")),
        "ftvnews.com.tw" to SiteCfg(listOf("article", "div.news-article", "div.article-body"), listOf("h1", "h1")),
        "民視" to SiteCfg(listOf("div.news-content", "article"), listOf("h1", "h1")),
        "storm.mg" to SiteCfg(listOf("div.article-body", "article"), listOf("h1.article-title", "h1")),
        "businesstoday.com.tw" to SiteCfg(listOf("div.article-content", "article"), listOf("h1", "h1")),
        "commonhealth.com.tw" to SiteCfg(listOf("div.article-content", "article"), listOf("h1", "h1")),
        "cheers.com.tw" to SiteCfg(listOf("div.article-content", "article"), listOf("h1", "h1")),
        "wealth.com.tw" to SiteCfg(listOf("div.article-content", "article"), listOf("h1", "h1")),
        "bnext.com.tw" to SiteCfg(listOf("div.post-content", "article"), listOf("h1.post-title", "h1")),
        "technews.tw" to SiteCfg(listOf("div.indent", "article"), listOf("h1", "h1")),
        "inside.com.tw" to SiteCfg(listOf("div.post-content", "article"), listOf("h1", "h1")),
        "yahoo.com" to SiteCfg(listOf("div.caas-body", "article"), listOf("h1.caas-title", "h1")),
        "msn.com" to SiteCfg(listOf("div.articlebody", "article"), listOf("h1", "h1")),
        "pts.org.tw" to SiteCfg(listOf("div.post-content", "article"), listOf("h1", "h1")),
        "cts.com.tw" to SiteCfg(listOf("div.news-content", "article"), listOf("h1", "h1")),
        "ttv.com.tw" to SiteCfg(listOf("div.news-content", "article"), listOf("h1", "h1")),
        "ctv.com.tw" to SiteCfg(listOf("div.news-content", "article"), listOf("h1", "h1")),
        "mirrormedia.mg" to SiteCfg(listOf("div.article-body", "article"), listOf("h1", "h1")),
        "twreporter.org" to SiteCfg(listOf("div.article-body", "article"), listOf("h1", "h1")),
        "newtalk.tw" to SiteCfg(listOf("div.news-content", "article"), listOf("h1", "h1")),
        "nownews.com" to SiteCfg(listOf("div.article-content", "article"), listOf("h1", "h1")),
        "ebc.net.tw" to SiteCfg(listOf("div.article-content", "article"), listOf("h1", "h1")),
        "nextapple.com" to SiteCfg(listOf("div.ndArticle_content", "article"), listOf("h1", "h1")),
        "cnyes.com" to SiteCfg(
            listOf("div.article-content", "div#articleContent", "div#content"),
            listOf("h1.article-title", "h1.title", "h1")
        )
    )

    /** 找不到網站設定時的通用選擇器 */
    val GENERIC_CONTENT_SELECTORS = listOf(
        "[itemprop=\"articleBody\"]",
        "div.article-content",
        "div.post-content",
        "div.entry-content",
        "div.news-content",
        "article",
        "main"
    )

    /** 這些字串出現就視為廣告／導覽雜訊 */
    val BLACKLIST = listOf(
        "請繼續往下閱讀", "最佳瀏覽效果", "建議使用", "點我訂閱",
        "延伸閱讀", "相關報導", "點我下載", "快訊／", "圖／", "攝／",
        "封面圖片來源", "★【理財達人秀】", "本文授權", "本文轉載",
        "加入會員", "訂閱電子報", "追蹤我們", "分享給朋友",
        "更多新聞", "你可能會喜歡", "相關文章", "熱門新聞"
    )

    private val BLACKLIST_RE: Regex =
        Regex(BLACKLIST.joinToString("|") { Regex.escape(it) })

    private val SKIP_KEYWORDS = listOf("更多新聞", "你可能會喜歡", "相關文章", "熱門新聞")

    fun forDomain(domain: String): SiteCfg? =
        MAP.firstOrNull { domain.contains(it.first) }?.second

    /** 是否為要丟棄的雜訊行 */
    fun isJunk(text: String): Boolean {
        if (BLACKLIST_RE.containsMatchIn(text)) return true
        if (text.startsWith("（圖") || text.startsWith("(圖")) return true
        if (text.startsWith("圖／") || text.startsWith("圖/")) return true
        if (SKIP_KEYWORDS.any { text.contains(it) }) return true
        return false
    }
}
