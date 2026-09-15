package com.kakaanime.provider.extractor.extractors

import com.kakaanime.provider.ProviderStream
import com.kakaanime.provider.StreamType
import com.kakaanime.provider.extractor.StreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

class OtakudesuHostExtractor : StreamExtractor {
    override val id = "otakudesu-host"
    override val priority = 115

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun canHandle(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
        return listOf("filedon", "uservideo", "userdrive", "samevideo", "vidhide", "blogger", "blogspot", "mp4upload", "yourupload", "yuplod", "streamwish", "filelions").any(host::contains)
    }

    override suspend fun extract(url: String, referer: String?): List<ProviderStream> = withContext(Dispatchers.IO) {
        val html = get(url, referer) ?: return@withContext emptyList()
        val results = linkedMapOf<String, ProviderStream>()

        Regex("<div[^>]+id=[\\\"']app[\\\"'][^>]+data-page=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { raw ->
                runCatching {
                    val page = JSONObject(decodeHtml(raw))
                    val media = page.optJSONObject("props")?.optString("url")?.takeIf(String::isNotBlank)
                    if (media != null) addMedia(results, resolveUrl(url, media) ?: media, url)
                }
            }

        listOf(
            "<source[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)",
            "<video[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)",
            "<(?:iframe|embed)[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)"
        ).forEach { pattern ->
            Regex(pattern, RegexOption.IGNORE_CASE).findAll(html).forEach { match ->
                resolveUrl(url, decodeHtml(match.groupValues[1]))?.let { resolved -> if (resolved != url) addMedia(results, resolved, url) }
            }
        }

        Regex("https?://[^\\s\\\"'<>]+\\.(?:m3u8|mpd|mp4|mkv|webm)(?:\\?[^\\s\\\"'<>]*)?", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { addMedia(results, it.value, url) }

        results.values.toList()
    }

    private fun addMedia(results: MutableMap<String, ProviderStream>, url: String, referer: String) {
        val clean = url.trim()
        if (!clean.startsWith("http", true)) return
        val path = clean.substringBefore('?').substringBefore('#')
        val type = when {
            path.endsWith(".m3u8", true) -> StreamType.HLS
            path.endsWith(".mpd", true) -> StreamType.DASH
            else -> StreamType.MP4.takeIf { path.endsWith(".mp4", true) || path.endsWith(".mkv", true) || path.endsWith(".webm", true) } ?: StreamType.UNKNOWN
        }
        results.putIfAbsent(clean, ProviderStream("otakudesu", clean, type = type, headers = mapOf("User-Agent" to UA, "Referer" to referer)))
    }

    private suspend fun get(url: String, referer: String?): String? = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8")
            if (!referer.isNullOrBlank()) builder.header("Referer", referer)
            client.newCall(builder.build()).execute().use { response -> if (response.isSuccessful) response.body?.string() else null }
        }.getOrNull()
    }

    private fun resolveUrl(base: String, candidate: String): String? = runCatching { URI(base).resolve(candidate).toString() }.getOrNull()

    private fun decodeHtml(value: String): String = value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private companion object { const val UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36" }
}
