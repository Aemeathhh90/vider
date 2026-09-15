package com.kakaanime.provider.extractor.extractors

import com.kakaanime.provider.ProviderStream
import com.kakaanime.provider.StreamType
import com.kakaanime.provider.extractor.StreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

class OtakudesuServerExtractor : StreamExtractor {
    override val id = "otakudesu-server"
    override val priority = 100
    private val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS).build()
    private val genericEmbed = GenericEmbedExtractor()
    private val kraken = KrakenFilesExtractor()
    private val pixeldrain = PixelDrainExtractor()

    override fun canHandle(url: String): Boolean {
        val v = url.trim().lowercase()
        return v.startsWith("http") && v.contains("otakudesu.") && (v.contains("/episode") || v.contains("-episode-"))
    }

    override suspend fun extract(url: String, referer: String?): List<ProviderStream> = withContext(Dispatchers.IO) {
        val html = get(url, referer) ?: return@withContext emptyList()
        val results = linkedMapOf<String, ProviderStream>()
        discoverDownloadLinks(url, html).forEach { candidate -> resolveExternal(candidate.url, url, candidate.quality).forEach { results.putIfAbsent(it.url, it) } }
        if (results.isEmpty()) extractInlineMedia(html, url).forEach { media -> results.putIfAbsent(media, ProviderStream(id, media, type = media.toStreamType(), headers = mediaHeaders(url))) }
        results.values.toList()
    }

    private suspend fun resolveExternal(url: String, referer: String, quality: String?): List<ProviderStream> {
        val clean = resolveRedirect(url, referer) ?: url
        val q = quality ?: Regex("(\\d{3,4})[pP]").find(clean)?.groupValues?.getOrNull(1)
        return when {
            clean.isDirectMediaUrl() -> listOf(ProviderStream(id, clean, q, type = clean.toStreamType(), headers = mediaHeaders(referer)))
            clean.contains("pixeldrain.com", true) -> pixeldrain.extract(clean, referer).map { it.copy(providerId = id, quality = it.quality ?: q) }
            clean.contains("krakenfiles.com", true) -> kraken.extract(clean, referer).map { it.copy(providerId = id, quality = it.quality ?: q) }
            else -> genericEmbed.extract(clean, referer).map { it.copy(providerId = id, quality = it.quality ?: q) }
        }
    }

    private suspend fun discoverDownloadLinks(pageUrl: String, html: String): List<Candidate> = buildList {
        Regex("<li[^>]*>(.*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).forEach { m ->
            val block = m.groupValues[1]
            val q = Regex("(\\d{3,4})\\s*[pP]").find(block)?.groupValues?.getOrNull(1)
            Regex("href\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE).findAll(block).forEach { h -> resolveUrl(pageUrl, h.groupValues[1])?.let { add(Candidate(it, q)) } }
        }
    }.distinctBy { it.url }

    private fun extractInlineMedia(html: String, base: String): List<String> {
        val out = linkedSetOf<String>()
        Regex("(?:file|src|source|hls|m3u8|videoUrl|video_url|playlist)\\s*[=:]\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE).findAll(html).forEach { m -> resolveUrl(base, m.groupValues[1])?.takeIf { it.isDirectMediaUrl() }?.let(out::add) }
        Regex("https?://[^\\s\\\"'<>]+\\.(?:m3u8|mpd|mp4|mkv|webm)(?:\\?[^\\s\\\"'<>]*)?", RegexOption.IGNORE_CASE).findAll(html).forEach { out.add(it.value) }
        return out.toList()
    }

    private suspend fun get(url: String, referer: String?): String? = withContext(Dispatchers.IO) { runCatching { Request.Builder().url(url).header("User-Agent", UA).apply { if (!referer.isNullOrBlank()) header("Referer", referer) }.build().let { client.newCall(it).execute().use { r -> if (r.isSuccessful) r.body?.string() else null } } }.getOrNull() }
    private suspend fun resolveRedirect(url: String, referer: String): String? = withContext(Dispatchers.IO) { runCatching { Request.Builder().url(url).header("User-Agent", UA).header("Referer", referer).build().let { client.newCall(it).execute().use { r -> r.request.url.toString() } } }.getOrNull() }
    private fun resolveUrl(base: String, candidate: String): String? = runCatching { URI(base).resolve(candidate).toString() }.getOrNull()
    private fun String.isDirectMediaUrl(): Boolean { val x = substringBefore('?').substringBefore('#').lowercase(); return x.endsWith(".m3u8") || x.endsWith(".mpd") || x.endsWith(".mp4") || x.endsWith(".mkv") || x.endsWith(".webm") }
    private fun String.toStreamType(): StreamType { val x = substringBefore('?').substringBefore('#').lowercase(); return when { x.endsWith(".m3u8") -> StreamType.HLS; x.endsWith(".mpd") -> StreamType.DASH; else -> StreamType.MP4 } }
    private fun mediaHeaders(referer: String) = mapOf("User-Agent" to UA, "Referer" to referer)
    private data class Candidate(val url: String, val quality: String?)
    private companion object { const val UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36" }
}
