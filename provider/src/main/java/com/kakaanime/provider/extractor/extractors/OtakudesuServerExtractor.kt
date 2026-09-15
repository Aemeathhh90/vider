package com.kakaanime.provider.extractor.extractors

import android.util.Base64
import com.kakaanime.provider.ProviderStream
import com.kakaanime.provider.StreamType
import com.kakaanime.provider.extractor.StreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/** Otakudesu: episode -> mirror AJAX -> embed host -> final media URL. */
class OtakudesuServerExtractor : StreamExtractor {
    override val id = "otakudesu-server"
    override val priority = 100

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    private val genericEmbed = GenericEmbedExtractor()
    private val kraken = KrakenFilesExtractor()
    private val pixeldrain = PixelDrainExtractor()

    override fun canHandle(url: String): Boolean {
        val value = url.trim().lowercase()
        return value.startsWith("http") && value.contains("otakudesu.") &&
            (value.contains("/episode") || value.contains("-episode-"))
    }

    override suspend fun extract(url: String, referer: String?): List<ProviderStream> = withContext(Dispatchers.IO) {
        val html = get(url, referer) ?: return@withContext emptyList()
        val results = linkedMapOf<String, ProviderStream>()

        resolveMirrorStream(url, html).forEach { candidate ->
            resolveExternal(candidate.url, url, candidate.quality).forEach { results.putIfAbsent(it.url, it) }
        }
        discoverDownloadLinks(url, html).forEach { candidate ->
            resolveExternal(candidate.url, url, candidate.quality).forEach { results.putIfAbsent(it.url, it) }
        }
        if (results.isEmpty()) {
            extractInlineMedia(html, url).forEach { media ->
                results.putIfAbsent(media, ProviderStream("otakudesu", media, type = media.toStreamType(), headers = mediaHeaders(url)))
            }
        }
        results.values.toList()
    }

    private suspend fun resolveMirrorStream(pageUrl: String, html: String): List<PlaybackCandidate> {
        val origin = runCatching { URI(pageUrl).let { "${it.scheme}://${it.authority}" } }.getOrNull() ?: return emptyList()
        val ajaxReferer = "$origin/"
        val script = Regex("<script[^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html).map { it.groupValues[1] }
            .firstOrNull {
                it.contains("window.__x__nonce", true) ||
                    (it.contains("{action:", true) && it.contains("action:\"", true)) ||
                    it.contains("mirrorstream", true)
            }
            ?: return emptyList()

        val nonceAction = NONCE_ACTION_REGEX.find(script)?.groupValues?.getOrNull(1)
            ?: script.substringAfter("data:{action:\"").substringBefore('"')
        val action = ACTION_REGEX.find(script)?.groupValues?.getOrNull(1)
            ?: FALLBACK_ACTION_REGEX.find(script)?.groupValues?.getOrNull(1)
            ?: script.substringAfter("action:\"").substringBefore('"')
        if (nonceAction.isBlank() || action.isBlank()) return emptyList()

        val nonceRaw = postAjaxRaw(
            "$origin/wp-admin/admin-ajax.php",
            mapOf("action" to nonceAction),
            ajaxReferer,
        ) ?: return emptyList()
        val nonce = extractAjaxData(nonceRaw) ?: return emptyList()

        val entries = Regex("data-content\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
            .findAll(html)
            .mapNotNull { parseMirrorEntry(decodeHtml(it.groupValues[1])) }
            .toList()

        val results = linkedMapOf<String, PlaybackCandidate>()
        for (entry in entries.distinctBy { "${it.id}|${it.i}|${it.q}" }) {
            val response = postAjaxRaw(
                "$origin/wp-admin/admin-ajax.php",
                mapOf("id" to entry.id, "i" to entry.i, "q" to entry.q, "nonce" to nonce, "action" to action),
                ajaxReferer,
            ) ?: continue
            val decoded = extractAjaxData(response)?.let(::decodeBase64) ?: decodeBase64(response) ?: response
            extractIframeUrls(decoded, pageUrl).forEach { results.putIfAbsent(it, PlaybackCandidate(it, entry.q)) }
            extractInlineMedia(decoded, pageUrl).forEach { results.putIfAbsent(it, PlaybackCandidate(it, entry.q)) }
        }
        return results.values.toList()
    }

    private suspend fun resolveExternal(url: String, referer: String, quality: String? = null): List<ProviderStream> {
        val clean = resolveRedirect(url, referer) ?: url
        val normalizedQuality = quality ?: Regex("(\\d{3,4})[pP]").find(clean)?.groupValues?.getOrNull(1)
        return when {
            clean.isDirectMediaUrl() -> listOf(ProviderStream("otakudesu", clean, quality = normalizedQuality, type = clean.toStreamType(), headers = mediaHeaders(referer)))
            clean.contains("pixeldrain.com", true) -> pixeldrain.extract(clean, referer).map { it.copy(providerId = "otakudesu", quality = it.quality ?: normalizedQuality) }
            clean.contains("krakenfiles.com", true) -> kraken.extract(clean, referer).map { it.copy(providerId = "otakudesu", quality = it.quality ?: normalizedQuality) }
            clean.contains("desustream", true) || clean.contains("desudrive", true) || clean.contains("odstream", true) || clean.contains("odcdn", true) || clean.contains("otakuwatch", true) -> resolveDesuStream(clean, referer, normalizedQuality)
            clean.contains("mp4upload", true) -> resolveMp4Upload(clean, referer, normalizedQuality)
            clean.contains("yourupload", true) || clean.contains("yuplod", true) -> {
                val id = Regex("[?&]id=([^&]+)", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.getOrNull(1)
                val normalized = if (!id.isNullOrBlank()) "https://yourupload.com/embed/$id" else clean
                resolveGenericEmbed(normalized, referer, normalizedQuality)
            }
            clean.contains("vidhide", true) -> resolveGenericEmbed(clean, referer, normalizedQuality)
            else -> resolveGenericEmbed(clean, referer, normalizedQuality)
        }
    }

    private suspend fun resolveDesuStream(url: String, referer: String, quality: String?): List<ProviderStream> {
        val html = get(url, referer) ?: return emptyList()
        val scripts = Regex("<script[^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html).map { it.groupValues[1] }.toList()
        val rawSource = Regex("(?:file|src)\\s*['\"]?\\s*[:=]\\s*['\"]([^'\"]+)", RegexOption.IGNORE_CASE)
            .find(scripts.joinToString("\n"))?.groupValues?.getOrNull(1)
            ?: Regex("https?://[^\\s\\\"'<>]+\\.(?:m3u8|mp4)(?:\\?[^\\s\\\"'<>]*)?", RegexOption.IGNORE_CASE)
                .find(html)?.value
        val media = rawSource?.let { resolveUrl(url, decodeHtml(it)) }
        return if (media != null) {
            listOf(ProviderStream("otakudesu", media, quality, type = media.toStreamType(), headers = mediaHeaders(url)))
        } else {
            resolveGenericEmbed(url, referer, quality)
        }
    }

    private suspend fun resolveMp4Upload(url: String, referer: String, quality: String?): List<ProviderStream> {
        val html = get(url, referer) ?: return emptyList()
        val script = Regex("<script[^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html).map { it.groupValues[1] }.firstOrNull { it.contains("player.src", true) }
        val media = script?.let {
            Regex("src\\s*:\\s*['\"]([^'\"]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1)
        }?.let { resolveUrl(url, decodeHtml(it)) }
        return if (media != null) {
            listOf(ProviderStream("otakudesu", media, quality, type = media.toStreamType(), headers = mediaHeaders(url)))
        } else {
            resolveGenericEmbed(url, referer, quality)
        }
    }

    private suspend fun resolveGenericEmbed(url: String, referer: String, quality: String?): List<ProviderStream> =
        genericEmbed.extract(url, referer).map { it.copy(providerId = "otakudesu", quality = it.quality ?: quality) }

    private suspend fun discoverDownloadLinks(pageUrl: String, html: String): List<PlaybackCandidate> {
        val result = mutableListOf<PlaybackCandidate>()
        Regex("<li[^>]*>(.*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).forEach { match ->
            val block = match.groupValues[1]
            val quality = Regex("(\\d{3,4})\\s*[pP]").find(block)?.groupValues?.getOrNull(1)
            Regex("href\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE).findAll(block).forEach { href ->
                resolveUrl(pageUrl, decodeHtml(href.groupValues[1]))?.let { result += PlaybackCandidate(it, quality) }
            }
        }
        return result.distinctBy { it.url }
    }

    private fun extractIframeUrls(html: String, baseUrl: String): List<String> = Regex("<iframe[^>]+(?:src|data-src)\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
        .findAll(html).mapNotNull { resolveUrl(baseUrl, decodeHtml(it.groupValues[1])) }.distinct().toList()

    private fun extractInlineMedia(html: String, baseUrl: String): List<String> {
        val urls = linkedSetOf<String>()
        Regex("(?:file|src|source|hls|m3u8|videoUrl|video_url|playlist)\\s*[=:]\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { match -> resolveUrl(baseUrl, decodeHtml(match.groupValues[1]))?.takeIf { it.isDirectMediaUrl() }?.let(urls::add) }
        Regex("https?://[^\\s\\\"'<>]+\\.(?:m3u8|mpd|mp4|mkv|webm)(?:\\?[^\\s\\\"'<>]*)?", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { urls.add(it.value) }
        return urls.toList()
    }

    private fun parseMirrorEntry(value: String): MirrorEntry? {
        val raw = decodeHtml(value).trim()
        val decoded = decodeBase64(raw)?.trim()?.removePrefix("[")?.removeSuffix("]") ?: return null
        val json = runCatching { JSONObject(decoded) }.getOrNull()
        if (json != null) {
            val id = json.optString("id").takeIf(String::isNotBlank)
            val mirror = json.optString("i").takeIf(String::isNotBlank)
            val quality = json.optString("q").takeIf(String::isNotBlank)
            if (id != null && mirror != null && quality != null) return MirrorEntry(id, mirror, quality)
        }
        return null
    }

    private suspend fun postAjaxRaw(endpoint: String, fields: Map<String, String>, referer: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .header("User-Agent", UA)
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string()?.trim() else null
            }
        }.getOrNull()
    }

    private fun extractAjaxData(raw: String): String? {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        return json?.optString("data")?.takeIf(String::isNotBlank)
            ?: Regex("[\"']data[\"']\\s*:\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                .find(raw)?.groupValues?.getOrNull(1)
            ?: raw.substringAfter(":\\\"").substringBefore('"').takeIf { it.isNotBlank() && it != raw }
    }

    private suspend fun resolveRedirect(url: String, referer: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            Request.Builder().url(url).header("User-Agent", UA).header("Referer", referer).build().let { request ->
                client.newCall(request).execute().use { it.request.url.toString() }
            }
        }.getOrNull()
    }

    private suspend fun get(url: String, referer: String?): String? = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8")
            if (!referer.isNullOrBlank()) builder.header("Referer", referer)
            client.newCall(builder.build()).execute().use { response -> if (response.isSuccessful) response.body?.string() else null }
        }.getOrNull()
    }

    private fun decodeBase64(value: String?): String? = runCatching { String(Base64.decode(value?.trim(), Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
    private fun resolveUrl(base: String, candidate: String): String? = runCatching { URI(base).resolve(candidate).toString() }.getOrNull()
    private fun decodeHtml(value: String): String = value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
    private fun String.isDirectMediaUrl(): Boolean { val clean = substringBefore('?').substringBefore('#').lowercase(); return clean.endsWith(".m3u8") || clean.endsWith(".mpd") || clean.endsWith(".mp4") || clean.endsWith(".mkv") || clean.endsWith(".webm") }
    private fun String.toStreamType(): StreamType { val clean = substringBefore('?').substringBefore('#').lowercase(); return when { clean.endsWith(".m3u8") -> StreamType.HLS; clean.endsWith(".mpd") -> StreamType.DASH; else -> StreamType.MP4 } }
    private fun mediaHeaders(referer: String) = mapOf("User-Agent" to UA, "Referer" to referer)

    private data class PlaybackCandidate(val url: String, val quality: String?)
    private data class MirrorEntry(val id: String, val i: String, val q: String)

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"
        private val NONCE_ACTION_REGEX = Regex("data\\s*:\\s*\\{\\s*action\\s*:\\s*\"([a-f0-9]+)\"", RegexOption.IGNORE_CASE)
        private val ACTION_REGEX = Regex("nonce\\s*:\\s*[^,]+,\\s*action\\s*:\\s*\"([a-f0-9]+)\"", RegexOption.IGNORE_CASE)
        private val FALLBACK_ACTION_REGEX = Regex("action\\s*:\\s*\"([a-f0-9]{32})\"", RegexOption.IGNORE_CASE)
    }
}
