package com.kakaanime.provider.extractor.extractors

import com.kakaanime.provider.ProviderStream
import com.kakaanime.provider.StreamType
import com.kakaanime.provider.extractor.BrowserStreamResolver
import com.kakaanime.provider.extractor.ExtractorRegistry
import com.kakaanime.provider.extractor.StreamExtractor
import com.kakaanime.provider.extractor.StreamResolver
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** Samehadaku episode-page resolver; host URLs are delegated to the normal extractor chain. */
class SamehadakuEpisodeExtractor(browserResolver: BrowserStreamResolver? = null) : StreamExtractor {
    override val id = "samehadaku-episode"
    override val priority = 120
    private val mainHost = "v2.samehadaku.how"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).followRedirects(true).build()
    private val hostResolver = StreamResolver(
        ExtractorRegistry(includeSamehadakuEpisodeExtractor = false, browserResolver = browserResolver),
        browserResolver = browserResolver
    )

    override fun canHandle(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (!host.contains(mainHost)) return false
        return url.contains("episode", true) || url.contains("/eps-", true) || url.contains("/one-piece-", true)
    }

    override suspend fun extract(url: String, referer: String?): List<ProviderStream> {
        val page = getPage(url) ?: return emptyList()
        val discovered = linkedMapOf<String, DiscoveredLink>()
        page.select("div#downloadb li a[href]").forEach { anchor ->
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }.trim()
            if (href.startsWith("http", true)) {
                val quality = anchor.parent()?.selectFirst("strong")?.text()?.trim()
                    ?: anchor.closest("li")?.selectFirst("strong")?.text()?.trim()
                discovered.putIfAbsent(href, DiscoveredLink(href, quality))
            }
        }
        page.select("#server > ul > li > div").forEach { server ->
            val post = server.attr("data-post").trim(); val nume = server.attr("data-nume").trim(); val type = server.attr("data-type").trim()
            if (post.isBlank() || nume.isBlank() || type.isBlank()) return@forEach
            requestPlayerAjax(url, post, nume, type)?.let { embed ->
                discovered.putIfAbsent(embed, DiscoveredLink(embed, server.selectFirst("span")?.text()?.trim()))
            }
        }
        if (discovered.isEmpty()) page.selectFirst("iframe[src], iframe[data-src]")?.let { iframe ->
            val href = iframe.absUrl("src").ifBlank { iframe.attr("src") }.ifBlank { iframe.attr("data-src") }.trim()
            if (href.startsWith("http", true)) discovered[href] = DiscoveredLink(href, null)
        }
        if (discovered.isEmpty()) return emptyList()

        val streams = mutableListOf<ProviderStream>()
        for (link in discovered.values) {
            val resolved = if (isDirectMedia(link.url)) {
                listOf(directStream(link.url, url, link.quality))
            } else {
                val hostResolved = hostResolver.resolve(listOf(link.url), url)
                val typed = hostResolved.filter { it.type != StreamType.UNKNOWN }
                if (typed.isNotEmpty()) typed else resolveEmbedPage(link.url, url, link.quality).ifEmpty { hostResolved }
            }
            streams += resolved.map { if (link.quality.isNullOrBlank()) it else it.copy(quality = link.quality) }
        }
        return streams.distinctBy { it.url }
    }

    private fun getPage(url: String) = runCatching {
        Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8").build().let { request ->
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.string()?.takeIf { it.isNotBlank() }?.let { Jsoup.parse(it, url) }
                }
            }
    }.getOrNull()

    private fun resolveEmbedPage(embedUrl: String, episodeUrl: String, quality: String?): List<ProviderStream> {
        val document = runCatching {
            val request = Request.Builder().url(embedUrl).header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
                .header("Referer", embedUrl.ifBlank { episodeUrl }).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()?.takeIf { it.isNotBlank() }?.let { Jsoup.parse(it, embedUrl) }
            }
        }.getOrNull() ?: return emptyList()
        val mediaUrls = linkedSetOf<String>()
        document.select("video source[src], video source[data-src], source[src], source[data-src]").forEach { source ->
            val href = source.absUrl("src").ifBlank { source.attr("src") }.ifBlank { source.absUrl("data-src") }.ifBlank { source.attr("data-src") }.trim()
            if (href.startsWith("http", true)) mediaUrls += href
        }
        document.select("video[src], video[data-src]").forEach { video ->
            val href = video.absUrl("src").ifBlank { video.attr("src") }.ifBlank { video.absUrl("data-src") }.ifBlank { video.attr("data-src") }.trim()
            if (href.startsWith("http", true)) mediaUrls += href
        }
        document.selectFirst("#app[data-page], [data-page]")?.attr("data-page")?.let(::extractDataPageUrl)
            ?.takeIf { it.startsWith("http", true) }?.let { mediaUrls += it }
        return mediaUrls.map { ProviderStream("samehadaku", it, quality, "Japanese", "Indonesian", streamTypeFromUrl(it), mapOf("User-Agent" to USER_AGENT, "Referer" to embedUrl)) }
    }

    private fun requestPlayerAjax(episodeUrl: String, post: String, nume: String, type: String): String? = runCatching {
        val body = FormBody.Builder().add("action", "player_ajax").add("post", post).add("nume", nume).add("type", type).build()
        val request = Request.Builder().url("https://$mainHost/wp-admin/admin-ajax.php").post(body)
            .header("User-Agent", USER_AGENT).header("Referer", episodeUrl).header("Origin", "https://$mainHost")
            .header("X-Requested-With", "XMLHttpRequest").header("Accept", "*/*").build()
        client.newCall(request).execute().use { response -> if (!response.isSuccessful) null else extractUrl(response.body?.string().orEmpty(), episodeUrl) }
    }.getOrNull()

    private fun extractUrl(value: String, baseUrl: String): String? {
        val document = Jsoup.parse(value, baseUrl)
        document.selectFirst("iframe[src], iframe[data-src]")?.let { iframe ->
            val href = iframe.absUrl("src").ifBlank { iframe.attr("src") }.ifBlank { iframe.attr("data-src") }
            if (href.isNotBlank()) return href.trim()
        }
        return Regex("(?:src|file|source|url)\\s*[:=]\\s*[\\\"']([^\\\"']+)").find(value)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractDataPageUrl(value: String): String? {
        val raw = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value).replace("\\/", "/").replace("\\\"", "\"")
        return Regex("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(raw)?.groupValues?.getOrNull(1)?.trim()
            ?: Regex("(?:^|[,{])\\s*url\\s*[:=]\\s*[\\\"']([^\\\"']+)").find(raw)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun isDirectMedia(url: String) = url.contains(".m3u8", true) || url.contains(".mpd", true) || url.contains(".mp4", true) || url.contains(".webm", true)
    private fun directStream(url: String, referer: String, quality: String?) = ProviderStream("samehadaku", url, quality, "Japanese", "Indonesian", streamTypeFromUrl(url), mapOf("User-Agent" to USER_AGENT, "Referer" to referer))
    private fun streamTypeFromUrl(url: String) = when { url.contains(".m3u8", true) -> StreamType.HLS; url.contains(".mpd", true) -> StreamType.DASH; url.contains(".mp4", true) || url.contains(".webm", true) -> StreamType.MP4; else -> StreamType.UNKNOWN }
    private data class DiscoveredLink(val url: String, val quality: String?)
    private companion object { const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36" }
}
