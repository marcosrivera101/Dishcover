package app.godishcover.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Restaurant and fast-food headlines from public RSS feeds. Only the headline, source and date
 * are shown; tapping opens the publisher's own page in the browser.
 */
object News {
    private val SOURCES = listOf(
        "Eater" to "https://www.eater.com/rss/index.xml",
        "QSR Magazine" to "https://www.qsrmagazine.com/feed/",
        "Restaurant Dive" to "https://www.restaurantdive.com/feeds/news/",
    )
    private const val MAX_AGE_MS = 60 * 60 * 1000L
    @Volatile private var cache: List<NewsItem> = emptyList()
    @Volatile private var cachedAt = 0L

    suspend fun load(force: Boolean = false): List<NewsItem> {
        if (!force && cache.isNotEmpty() && System.currentTimeMillis() - cachedAt < MAX_AGE_MS) return cache
        val all = coroutineScope {
            SOURCES.map { (name, url) ->
                async(Dispatchers.IO) { runCatching { fetch(name, url) }.getOrDefault(emptyList()) }
            }.awaitAll().flatten()
        }
        if (all.isNotEmpty()) {
            cache = all.sortedByDescending { it.publishedAt }.take(40)
            cachedAt = System.currentTimeMillis()
        }
        return cache
    }

    private suspend fun fetch(source: String, url: String): List<NewsItem> = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Dishcover/1.0 (Android)")
        }
        try {
            conn.inputStream.use { stream ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, null)
                val out = mutableListOf<NewsItem>()
                var inItem = false
                var title = ""; var link = ""; var date = ""; var summary = ""
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT && out.size < 15) {
                    val name = parser.name
                    if (event == XmlPullParser.START_TAG) {
                        when {
                            name == "item" || name == "entry" -> { inItem = true; title = ""; link = ""; date = ""; summary = "" }
                            inItem && name == "title" -> title = parser.nextText()
                            inItem && name == "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                val rel = parser.getAttributeValue(null, "rel")
                                if (href != null) { if (link.isEmpty() || rel == "alternate") link = href }
                                else link = parser.nextText()
                            }
                            inItem && (name == "pubDate" || name == "published" || name == "updated") ->
                                if (date.isEmpty()) date = parser.nextText()
                            inItem && (name == "description" || name == "summary") -> summary = parser.nextText()
                        }
                    } else if (event == XmlPullParser.END_TAG && (name == "item" || name == "entry")) {
                        inItem = false
                        if (title.isNotBlank() && link.startsWith("https://")) {
                            out += NewsItem(clean(title), link.trim(), source, parseDate(date), clean(summary).take(160))
                        }
                    }
                    event = parser.next()
                }
                out
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun clean(s: String): String = s
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&nbsp;", " ").replace("&rsquo;", "'").replace("&lsquo;", "'")
        .replace("&ldquo;", "\"").replace("&rdquo;", "\"").replace("&#8217;", "'").replace("&#8216;", "'")
        .replace("&#8220;", "\"").replace("&#8221;", "\"").replace("&#038;", "&").replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ").trim()

    private fun parseDate(s: String): Long {
        val t = s.trim()
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
        for (f in formats) {
            runCatching { SimpleDateFormat(f, Locale.US).parse(t)?.time }.getOrNull()?.let { return it }
        }
        return 0
    }
}
