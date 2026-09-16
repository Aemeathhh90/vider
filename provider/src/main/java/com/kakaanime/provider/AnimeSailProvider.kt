package com.kakaanime.provider

import com.kakaanime.provider.extractor.BrowserStreamResolver
import com.kakaanime.provider.extractor.ExtractorRegistry
import com.kakaanime.provider.extractor.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Native AnimeSail adapter.
 *
 * AnimeSail is HTML-first: search/detail/episode pages are regular web pages and
 * the episode page exposes mirror entries whose data-em payload contains an
 * iframe. The iframe is handed to the shared extractor pipeline instead of
 * returning an unverified URL directly to Media3.
 */
class AnimeSailProvider(
    private val browserResolver: BrowserStreamResolver? = null
) : AnimeProvider {
    override val id: String = "animesail"
    override val name: String = "AnimeSail"
    override val priority: Int = 310

    private val mainUrl = "https://154.26.137.28"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val resolver = StreamResolver(
        registry = ExtractorRegistry(browserResolver = browserResolver),
        browserResolver = browserResolver
    )

    override suspend fun search(query: String): List<ProviderAnime> = withContext(Dispatchers.IO) {
        val url = "$mainUrl/?s=${encode(query)}"
        val document = requestDocument(url) ?: return@withContext emptyList()
        document.select("div.listupd article").mapNotNull { it.toSearchAnime() }
    }

    override suspend fun getAnime(animeId: String): ProviderAnime? = withContext(Dispatchers.IO) {
        val url = animeId.removePrefix("$id:")
        val document = requestDocument(url) ?: return@withContext null
        parseAnime(document, url)
    }

    override suspend fun getEpisodes(animeId: String): List<ProviderEpisode> = withContext(Dispatchers.IO) {
        val url = animeId.removePrefix("$id:")
        val document = requestDocument(url) ?: return@withContext emptyList()
        document.select("ul.daftar > li a[href]").mapNotNull { link ->
            val number = episodeNumber(link.text()) ?: return@mapNotNull null
            val href = absoluteUrl(link.attr("href"), url) ?: return@mapNotNull null
            ProviderEpisode(
                id = "$id:$href",
                animeId = animeId,
                number = number,
                providerId = id,
                title = link.text().trim().ifBlank { "Episode $number" }
            )
        }.distinctBy { it.number }.sortedBy { it.number }
    }

    override suspend fun getStreams(animeId: String, episodeNumber: Int): List<ProviderStream> = withContext(Dispatchers.IO) {
        val episodes = getEpisodes(animeId)
        val episodeUrl = episodes.firstOrNull { it.number == episodeNumber }
            ?.id?.removePrefix("$id:")
            ?: return@withContext emptyList()

        val document = requestDocument(episodeUrl) ?: return@withContext emptyList()
        val mirrorUrls = document.select(".mobius > .mirror > option[data-em]").mapNotNull { option ->
            decodeIframe(option.attr("data-em"), episodeUrl)
        }.distinct()

        if (mirrorUrls.isEmpty()) return@withContext emptyList()

        val qualityByUrl = document.select(".mobius > .mirror > option[data-em]").associate { option ->
            decodeIframe(option.attr("data-em"), episodeUrl) to parseQuality(option.text())
        }

        val resolved = mirrorUrls.flatMap { mirror ->
            runCatching { resolver.resolve(listOf(mirror), referer = episodeUrl) }
                .getOrDefault(emptyList())
                .map { stream ->
                    stream.copy(
                        providerId = id,
                        quality = stream.quality ?: qualityByUrl[mirror],
                        language = stream.language ?: "Japanese",
                        subtitleLanguage = stream.subtitleLanguage ?: "Indonesian",
                        headers = if (stream.headers.containsKey("Referer")) stream.headers
                        else stream.headers + ("Referer" to mainUrl)
                    )
                }
        }

        ProviderStreamDeduplicator.deduplicate(resolved)
    }

    private fun parseAnime(document: org.jsoup.nodes.Document, url: String): ProviderAnime {
        val title = document.selectFirst("h1.entry-title")?.text()
            ?.replace("Subtitle Indonesia", "", ignoreCase = true)
            ?.trim()
            ?.ifBlank { null }
            ?: "AnimeSail"
        val poster = document.selectFirst("div.entry-content > img")?.attr("src")
            ?.let { absoluteUrl(it, url) }
        val description = document.selectFirst("div.entry-content > p")?.text()?.trim().orEmpty()
        val genres = document.select("tbody th:contains(Genre)").next().select("a").map { it.text().trim() }.filter { it.isNotBlank() }
        val year = document.select("tbody th:contains(Dirilis)").next().text().trim().toIntOrNull()
        val status = document.select("tbody th:contains(Status)").next().text().trim().ifBlank { "UNKNOWN" }
        val episodes = document.select("ul.daftar > li a").mapNotNull { episodeNumber(it.text()) }

        return ProviderAnime(
            id = "$id:$url",
            title = title,
            providerId = id,
            posterUrl = poster,
            description = description,
            genres = genres,
            year = year,
            status = status,
            latestEpisode = episodes.maxOrNull()
        )
    }

    private fun Element.toSearchAnime(): ProviderAnime? {
        val href = absoluteUrl(selectFirst("a")?.attr("href").orEmpty(), mainUrl) ?: return null
        val rawTitle = select(".tt > h2").text().trim().ifBlank { selectFirst("h2")?.text()?.trim().orEmpty() }
        if (rawTitle.isBlank()) return null
        val title = rawTitle.replace(Regex("\\s+Episode\\s+\\d+.*$", RegexOption.IGNORE_CASE), "").trim()
        val poster = selectFirst("div.limit img")?.attr("src")?.let { absoluteUrl(it, href) }
        return ProviderAnime(
            id = "$id:$href",
            title = title.ifBlank { rawTitle },
            providerId = id,
            posterUrl = poster
        )
    }

    private fun requestDocument(url: String): org.jsoup.nodes.Document? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Referer", mainUrl)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            response.body?.string()?.takeIf { it.isNotBlank() }?.let(Jsoup::parse)
        }
    }.getOrNull()

    private fun decodeIframe(encoded: String, referer: String): String? = runCatching {
        val decoded = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        val src = Jsoup.parse(decoded).selectFirst("iframe")?.attr("src")?.trim().orEmpty()
        absoluteUrl(src, referer)
    }.getOrNull()?.takeIf { it.startsWith("http", ignoreCase = true) }

    private fun absoluteUrl(value: String, base: String): String? = runCatching {
        if (value.isBlank()) null else URI(base).resolve(value).toString()
    }.getOrNull()

    private fun episodeNumber(value: String): Int? =
        Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("(?:^|[-_ /])ep(?:isode)?[-_ /]?(\\d+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun parseQuality(value: String): String? =
        Regex("(\\d{3,4}p)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)

    private fun encode(value: String): String = URLEncoder.encode(value.trim(), "UTF-8")

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/138.0 Mobile Safari/537.36"
    }
}
