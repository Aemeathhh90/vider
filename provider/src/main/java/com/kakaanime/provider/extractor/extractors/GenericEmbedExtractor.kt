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

class GenericEmbedExtractor : StreamExtractor {
    override val id = "generic-embed"
    override val priority = 20
    private val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()

    override fun canHandle(url: String): Boolean = url.trim().startsWith("http", true) && !url.isDirectMediaUrl()

    override suspend fun extract(url: String, referer: String?): List<ProviderStream> = withContext(Dispatchers.IO) {
        resolvePage(url, referer, 0, linkedSetOf())
    }

    private fun resolvePage(url: String, referer: String?, depth: Int, visited: MutableSet<String>): List<ProviderStream> {
        if (depth > 4 || !visited.add(url)) return emptyList()
        val request = Request.Builder().url(url).header("User-Agent", UA).apply { if (!referer.isNullOrBlank()) header("Referer", referer) }.build()
        val response = runCatching { client.newCall(request).execute() }.getOrNull() ?: return emptyList()
        response.use {
            if (!it.isSuccessful) return emptyList()
            val finalUrl = it.request.url.toString()
            val body = it.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            val direct = extractMediaUrls(body, finalUrl).map { media -> ProviderStream("resolver", media, type = media.toStreamType(), headers = mapOf("Referer" to finalUrl)) }
            if (direct.isNotEmpty()) return direct.distinctBy { it.url }
            for (iframe in extractIframeUrls(body, finalUrl)) {
                val nested = resolvePage(iframe, finalUrl, depth + 1, visited)
                if (nested.isNotEmpty()) return nested
            }
        }
        return emptyList()
    }

    private fun extractMediaUrls(html: String, base: String): List<String> {
        val urls = linkedSetOf<String>()
        val patterns = listOf(
            Regex("<(?:source|video)[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE),
            Regex("(?:file|src|source|hls|m3u8|videoUrl|video_url|playlist|contentUrl)\\s*[=:]\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE),
            Regex("https?://[^\\s\\\"'<>]+\\.(?:m3u8|mpd|mp4|mkv|webm)(?:\\?[^\\s\\\"'<>]*)?", RegexOption.IGNORE_CASE)
        )
        patterns.forEach { p -> p.findAll(html).forEach { m ->
            val raw = (m.groupValues.getOrNull(1)?.ifBlank { m.value } ?: m.value).trim()
                .replace("\\/", "/").replace("\\u0026", "&").replace("\\u003d", "=")
            runCatching { URI(base).resolve(raw).toString() }.getOrNull()?.takeIf { it.isDirectMediaUrl() }?.let(urls::add)
        } }
        return urls.toList()
    }

    private fun extractIframeUrls(html: String, base: String): List<String> = Regex("<iframe[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
        .findAll(html).mapNotNull { runCatching { URI(base).resolve(it.groupValues[1]).toString() }.getOrNull() }
        .filter { it.startsWith("http", true) }.distinct().toList()

    private fun String.isDirectMediaUrl(): Boolean { val x = substringBefore('?').substringBefore('#').lowercase(); return x.endsWith(".m3u8") || x.endsWith(".mpd") || x.endsWith(".mp4") || x.endsWith(".mkv") || x.endsWith(".webm") }
    private fun String.toStreamType(): StreamType { val x = substringBefore('?').substringBefore('#').lowercase(); return when { x.endsWith(".m3u8") -> StreamType.HLS; x.endsWith(".mpd") -> StreamType.DASH; else -> StreamType.MP4 } }
    private companion object { const val UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36" }
}
