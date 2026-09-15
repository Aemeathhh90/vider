package com.kakaanime.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

internal class OtakudesuWebSource {
    private val client = OkHttpClient.Builder()
        .followRedirects(true).followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build()

    private val sources = listOf(
        WebSource("fit", "https://otakudesu.fit", true),
        WebSource("blog", "https://otakudesu.blog", false),
        WebSource("id", "https://otakudesu.id", false),
        WebSource("ro", "https://otakudesu.ro", false),
        WebSource("cloud", "https://otakudesu.cloud", false)
    )

    suspend fun getAnime(slug: String): WebAnime? {
        for (source in sources) for (url in source.detailUrls(slug)) {
            val html = get(url) ?: continue
            if (!html.contains("anime", true)) continue
            val title = html.firstMatch("<h1[^>]*>(.*?)</h1>", "<title[^>]*>(.*?)</title>")
                ?.stripHtml()?.removeSuffix(" - Otaku desu")?.trim()
            if (!title.isNullOrBlank()) return WebAnime(url, title, html, source.id)
        }
        return null
    }

    suspend fun getEpisodes(anime: WebAnime): List<WebEpisode> = parseEpisodes(anime.url, anime.html)

    suspend fun getEpisodes(slug: String): List<WebEpisode> {
        val merged = linkedMapOf<Int, WebEpisode>()
        for (source in sources) for (url in source.detailUrls(slug)) {
            val html = get(url) ?: continue
            if (html.isBlank()) continue
            for (episode in parseEpisodes(url, html)) merged.putIfAbsent(episode.number, episode)
        }
        return merged.values.sortedBy { it.number }
    }

    suspend fun getEpisodePage(episode: WebEpisode): String? = get(episode.url)

    fun discoverPlaybackUrls(html: String, pageUrl: String): List<String> {
        val urls = linkedSetOf<String>()
        listOf(
            "<iframe[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"']",
            "(?:data-video|data-src|data-url)=[\\\"']([^\\\"']+)[\\\"']",
            "<source[^>]+src=[\\\"']([^\\\"']+)[\\\"']",
            "<video[^>]+src=[\\\"']([^\\\"']+)[\\\"']"
        ).forEach { pattern ->
            Regex(pattern, RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
                resolve(pageUrl, decodeHtml(m.groupValues[1].trim()))?.let { urls += it }
            }
        }
        return urls.toList()
    }

    private fun parseEpisodes(pageUrl: String, html: String): List<WebEpisode> {
        val result = linkedMapOf<Int, WebEpisode>()
        val anchorPattern = Regex("<a\\b[^>]*\\bhref\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (match in anchorPattern.findAll(html)) addEpisode(result, pageUrl, match.groupValues[1], match.groupValues[2])

        // Some Otakudesu mirrors/theme revisions put the episode URL in attributes
        // around a non-standard anchor. Keep a second permissive pass for those pages.
        val hrefPattern = Regex("(?:href|data-href|data-url)\\s*=\\s*[\\\"']([^\\\"']*(?:episode|eps|ep)[^\\\"']*)[\\\"']", RegexOption.IGNORE_CASE)
        for (match in hrefPattern.findAll(html)) addEpisode(result, pageUrl, match.groupValues[1], "")

        return result.values.sortedBy { it.number }
    }

    private fun addEpisode(result: MutableMap<Int, WebEpisode>, pageUrl: String, rawHref: String, rawText: String) {
        val href = decodeHtml(rawHref.trim())
        val text = rawText.stripHtml().trim()
        val url = resolve(pageUrl, href) ?: return
        if (!url.contains("episode", true)) return
        val number = extractEpisodeNumber(text, url) ?: return
        result.putIfAbsent(number, WebEpisode(url, number, text.ifBlank { "Episode $number" }))
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    private fun WebSource.detailUrls(slug: String): List<String> {
        val clean = slug.removePrefix("otakudesu:").trim('/')
        val fitSlug = clean.replace(Regex("-(?:subtitle-)?indonesia$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("-sub-indo$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^1piece$", RegexOption.IGNORE_CASE), "one-piece")
            .replace(Regex("^onepiece$", RegexOption.IGNORE_CASE), "one-piece")
        return if (seriesStyle) listOf("$baseUrl/series/${fitSlug.trim('/')}/")
        else listOf("$baseUrl/anime/${clean.trim('/')}/", "$baseUrl/anime/${clean.trim('/').removeSuffix("-sub-indo")}/").distinct()
    }

    private fun String.firstMatch(vararg patterns: String): String? = patterns.firstNotNullOfOrNull {
        Regex(it, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(this)?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)
    }
    private fun String.stripHtml(): String = replace(Regex("<[^>]+>"), " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()
    private fun decodeHtml(value: String): String = value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
    private fun resolve(base: String, candidate: String): String? = runCatching { URI(base).resolve(candidate).toString() }.getOrNull()
    private fun extractEpisodeNumber(title: String, href: String): Int? {
        val value = "$title $href"
        return Regex("(?:episode|eps|ep)[^0-9]*(\\d+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("(?:-|/)(\\d+)(?:-|/|$)").find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
    private data class WebSource(val id: String, val baseUrl: String, val seriesStyle: Boolean)
    internal data class WebAnime(val url: String, val title: String, val html: String, val sourceId: String)
    internal data class WebEpisode(val url: String, val number: Int, val title: String)
}
