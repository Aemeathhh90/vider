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

/** Host-specific extractor based on the proven KrakenFiles source flow. */
class KrakenFilesExtractor : StreamExtractor {
    override val id = "krakenfiles"
    override val priority = 85

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    override fun canHandle(url: String): Boolean =
        runCatching { URI(url).host.orEmpty().contains("krakenfiles.com", ignoreCase = true) }.getOrDefault(false)

    override suspend fun extract(url: String, referer: String?): List<ProviderStream> =
        withContext(Dispatchers.IO) {
            val pageUrl = if (url.contains("/view/", true)) {
                val id = Regex("/(?:view|embed-video)/([\\da-zA-Z]+)", RegexOption.IGNORE_CASE)
                    .find(url)?.groupValues?.getOrNull(1)
                    ?: return@withContext emptyList()
                "https://krakenfiles.com/embed-video/$id"
            } else url

            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
                .build()

            val html = runCatching {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string().orEmpty() else ""
                }
            }.getOrNull().orEmpty()
            if (html.isBlank()) return@withContext emptyList()

            val source = Regex(
                "<source[^>]+src=[\\\"']([^\\\"']+)[\\\"']",
                RegexOption.IGNORE_CASE
            ).findAll(html).map { it.groupValues[1] }
                .firstOrNull { it.contains("krakencloud", true) || it.contains(".mp4", true) }
                ?: return@withContext emptyList()

            val streamUrl = URI(pageUrl).resolve(decodeHtml(source)).toString()
            listOf(
                ProviderStream(
                    providerId = id,
                    url = streamUrl,
                    type = StreamType.MP4,
                    headers = mapOf("Referer" to pageUrl)
                )
            )
        }

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
