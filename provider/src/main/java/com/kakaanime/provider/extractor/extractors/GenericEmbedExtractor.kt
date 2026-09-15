package com.kakaanime.provider.extractor.extractors

import com.kakaanime.provider.ProviderStream
import com.kakaanime.provider.StreamType
import com.kakaanime.provider.extractor.StreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/** Generic final-stage player resolver with static media and iframe-chain support. */
class GenericEmbedExtractor : StreamExtractor {
    override val id = "generic-embed"
    override val priority = 20

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    override fun canHandle(url: String): Boolean {
        val value = url.trim()
        return value.startsWith("http", ignoreCase = true) && !value.isDirectMediaUrl()
    }

    override suspend fun extract(url: String, referer: String?): List<ProviderStream> =
        withContext(Dispatchers.IO) { resolvePage(url, referer, 0, linkedSetOf()) }

    private fun resolvePage(url: String, referer: String?, depth: Int, visited: MutableSet<String>): List<ProviderStream> {
        if (depth > MAX_DEPTH || !visited.add(url)) return emptyList()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
            .build()
        val response = runCatching { client.newCall(request).execute() }.getOrNull() ?: return emptyList()
        response.use {
            if (!it.isSuccessful) return emptyList()
            val finalUrl = it.request.url.toString()
            val body = it.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            val direct = extractMediaUrls(body, finalUrl).map { media ->
                ProviderStream("resolver", media, type = media.toStreamType(), headers = mapOf("Referer" to finalUrl))
            }
            if (direct.isNotEmpty()) return direct.distinctBy { it.url }
            extractIframeUrls(body, finalUrl).forEach { iframe ->
                val nested = resolvePage(iframe, finalUrl, depth + 1, visited)
                if (nested.isNotEmpty()) return nested
            }
        }
        return emptyList()
    }

    private fun extractMediaUrls(html: String, baseUrl: String): List<String> {
        val urls = linkedSetOf<String>()
        val patterns = listOf(
            Regex("<(?:source|video)[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE),
            Regex("(?:file|src|source|hls|m3u8|videoUrl|video_url|playlist|contentUrl)\\s*[=:]\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE),
            Regex("https?://[^\\s\\\"'<>]+\\.(?:m3u8|mpd|mp4|mkv|webm)(?:\\?[^\\s\\\"'<>]*)?", RegexOption.IGNORE_CASE),
            Regex("(?:https?:)?//[^\\s\\\"'<>]+\\.(?:m3u8|mpd|mp4|mkv|webm)(?:\\?[^\\s\\\"'<>]*)?", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) for (match in pattern.findAll(html)) {
            val raw = match.groupValues.getOrNull(1)?.ifBlank { match.value } ?: continue
            val absolute = resolveUrl(baseUrl, normalizeCandidate(raw)) ?: continue
            if (absolute.isDirectMediaUrl()) urls += absolute
        }
        return urls.toList()
    }

    private fun extractIframeUrls(html: String, baseUrl: String): List<String> =
        Regex("<iframe[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
            .findAll(html)
            .mapNotNull { resolveUrl(baseUrl, normalizeCandidate(it.groupValues[1])) }
            .filter { it.startsWith("http", ignoreCase = true) }
            .distinct().toList()

    private fun normalizeCandidate(value: String): String = decodeHtml(value.trim())
        .replace("\\/", "/").replace("\\u0026", "&").replace("\\u003d", "=")

    private fun resolveUrl(baseUrl: String, candidate: String): String? =
        runCatching { URI(baseUrl).resolve(candidate).toString() }.getOrNull()

    private fun decodeHtml(value: String): String = value.replace("&amp;", "&")
        .replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private fun String.isDirectMediaUrl(): Boolean {
        val clean = substringBefore('?').substringBefore('#').lowercase()
        return clean.endsWith(".m3u8") || clean.endsWith(".mpd") || clean.endsWith(".mp4") || clean.endsWith(".mkv") || clean.endsWith(".webm")
    }

    private fun String.toStreamType(): StreamType {
        val clean = substringBefore('?').substringBefore('#').lowercase()
        return when { clean.endsWith(".m3u8") -> StreamType.HLS; clean.endsWith(".mpd") -> StreamType.DASH; else -> StreamType.MP4 }
    }

    private companion object {
        const val MAX_DEPTH = 4
        const val UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
