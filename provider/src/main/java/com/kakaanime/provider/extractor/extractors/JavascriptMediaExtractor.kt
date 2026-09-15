package com.kakaanime.provider.extractor.extractors

import android.util.Base64
import com.kakaanime.provider.ProviderStream
import com.kakaanime.provider.StreamType
import com.kakaanime.provider.extractor.StreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** Extracts common media URLs embedded in JavaScript/configuration text without executing site JavaScript. */
class JavascriptMediaExtractor : StreamExtractor {
    override val id = "javascript-media"
    override val priority = 35

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun canHandle(url: String): Boolean =
        url.trim().startsWith("http", ignoreCase = true) && !url.trim().isDirectMediaUrl()

    override suspend fun extract(url: String, referer: String?): List<ProviderStream> = withContext(Dispatchers.IO) {
        val html = fetch(url, referer) ?: return@withContext emptyList()
        val candidates = linkedSetOf<String>()
        candidates += directMediaCandidates(html, url)
        candidates += encodedMediaCandidates(html, url)
        candidates += scriptStringCandidates(html, url)
        candidates.map { media ->
            ProviderStream("resolver", media, type = media.toStreamType(), headers = mapOf("Referer" to url))
        }
    }

    private fun fetch(url: String, referer: String?): String? = runCatching {
        val builder = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/javascript,*/*;q=0.8")
        if (!referer.isNullOrBlank()) builder.header("Referer", referer)
        client.newCall(builder.build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun directMediaCandidates(text: String, baseUrl: String): List<String> {
        val pattern = Regex("(?:https?:)?//[^\\s\\\"'<>\\\\]+\\.(?:m3u8|mpd|mp4|webm)(?:\\?[^\\s\\\"'<>\\\\]*)?", RegexOption.IGNORE_CASE)
        return pattern.findAll(unescape(text)).mapNotNull { resolve(baseUrl, it.value) }
            .filter { it.isDirectMediaUrl() }.distinct().toList()
    }

    private fun encodedMediaCandidates(text: String, baseUrl: String): List<String> {
        val results = linkedSetOf<String>()
        val atob = Regex("(?:atob|decodeBase64|base64_decode)\\s*\\(\\s*[\\\"']([^\\\"']+)[\\\"']\\s*\\)", RegexOption.IGNORE_CASE)
        atob.findAll(text).forEach { match ->
            decodeBase64(match.groupValues[1])?.let { decoded ->
                directMediaCandidates(decoded, baseUrl).forEach { results += it }
            }
        }
        Regex("[A-Za-z0-9+/_=-]{24,}").findAll(text).forEach { match ->
            decodeBase64(match.value)?.let { decoded ->
                if (decoded.contains("http", true) && decoded.contains(Regex("\\.(?:m3u8|mpd|mp4|webm)", RegexOption.IGNORE_CASE))) {
                    directMediaCandidates(decoded, baseUrl).forEach { results += it }
                }
            }
        }
        return results.toList()
    }

    private fun scriptStringCandidates(text: String, baseUrl: String): List<String> {
        val results = linkedSetOf<String>()
        val pattern = Regex("(?:file|src|source|hls|m3u8|videoUrl|video_url|playlist|contentUrl|stream)\\s*[:=]\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
        pattern.findAll(text).forEach { match ->
            val candidate = resolve(baseUrl, unescape(match.groupValues[1])) ?: return@forEach
            if (candidate.isDirectMediaUrl()) results += candidate
        }
        return results.toList()
    }

    private fun decodeBase64(value: String): String? = runCatching {
        String(Base64.decode(value.replace("-", "+").replace("_", "/"), Base64.DEFAULT), Charsets.UTF_8)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun unescape(value: String): String = runCatching {
        URLDecoder.decode(value.trim().replace("\\/", "/").replace("\\u0026", "&").replace("\\u003d", "=").replace("&amp;", "&"), "UTF-8")
    }.getOrDefault(value)

    private fun resolve(base: String, candidate: String): String? = runCatching { URI(base).resolve(candidate).toString() }.getOrNull()

    private fun String.isDirectMediaUrl(): Boolean {
        val clean = substringBefore('?').substringBefore('#').lowercase()
        return clean.endsWith(".m3u8") || clean.endsWith(".mpd") || clean.endsWith(".mp4") || clean.endsWith(".webm")
    }

    private fun String.toStreamType(): StreamType = when {
        substringBefore('?').substringBefore('#').lowercase().endsWith(".m3u8") -> StreamType.HLS
        substringBefore('?').substringBefore('#').lowercase().endsWith(".mpd") -> StreamType.DASH
        else -> StreamType.MP4
    }

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
