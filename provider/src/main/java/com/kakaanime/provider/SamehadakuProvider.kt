package com.kakaanime.provider

import com.kakaanime.provider.extractor.BrowserStreamResolver
import com.kakaanime.provider.extractor.ExtractorRegistry
import com.kakaanime.provider.extractor.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Samehadaku HTML-first adapter with JSON gateway fallbacks. */
class SamehadakuProvider(
    browserResolver: BrowserStreamResolver? = null
) : AnimeProvider {
    override val id = "samehadaku"
    override val name = "Samehadaku"
    override val priority = 20

    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).followRedirects(true).build()
    private val mainUrl = "https://v2.samehadaku.how"
    private val primary = "https://www.keyrafara.com/streaming/samehadaku"
    private val wajik = "https://wajik-anime-api.vercel.app/samehadaku"
    private val resolver = StreamResolver(ExtractorRegistry(browserResolver = browserResolver), browserResolver = browserResolver)

    override suspend fun search(query: String): List<ProviderAnime> {
        val normalized = query.trim(); if (normalized.isBlank()) return emptyList()
        for (url in listOf("$mainUrl/?s=${encode(normalized)}", "$mainUrl/search/${normalized.slugify()}/", "$mainUrl/search/?q=${encode(normalized)}").distinct()) {
            val document = requestDocument(url) ?: continue
            val results = document.extractSearchResults(); if (results.isNotEmpty()) return results
        }
        if (normalized.equals("one piece", true)) requestDocument("$mainUrl/anime/one-piece/")?.toProviderAnimeDetail()?.let { return listOf(it) }
        return searchGateway(normalized)
    }

    override suspend fun getAnime(animeId: String): ProviderAnime? {
        val raw = normalizeAnimeId(animeId)
        val url = when { raw.startsWith("http", true) -> raw; raw.startsWith("/anime/", true) -> "$mainUrl$raw"; else -> "$mainUrl/anime/${raw.trim('/')}/" }
        requestDocument(url)?.toProviderAnimeDetail()?.let { return it }
        return getAnimeGateway(raw)
    }

    override suspend fun getEpisodes(animeId: String): List<ProviderEpisode> {
        val raw = normalizeAnimeId(animeId)
        val url = when { raw.startsWith("http", true) -> raw; raw.startsWith("/anime/", true) -> "$mainUrl$raw"; else -> "$mainUrl/anime/${raw.trim('/')}/" }
        requestDocument(url)?.let { document ->
            val episodes = document.select("div.lstepsiode.listeps ul li, div.listeps ul li, div.episodelist ul li").mapNotNull { it.toProviderEpisode(raw) }.distinctBy { it.number }.sortedBy { it.number }
            if (episodes.isNotEmpty()) return episodes
        }
        return getEpisodesGateway(raw)
    }

    override suspend fun getStreams(animeId: String, episodeNumber: Int): List<ProviderStream> {
        val raw = normalizeAnimeId(animeId)
        val episode = getEpisodes("$id:$raw").firstOrNull { it.number == episodeNumber }
        val episodeUrl = episode?.id?.removePrefix("$id:")
        if (!episodeUrl.isNullOrBlank() && episodeUrl.startsWith("http", true)) {
            val resolved = resolver.resolve(listOf(episodeUrl), referer = "$mainUrl/"); if (resolved.isNotEmpty()) return resolved.map { it.copy(providerId = id) }
        }
        if (episode == null && raw.contains("one-piece", true)) {
            val resolved = resolver.resolve(listOf("$mainUrl/one-piece-episode-$episodeNumber/"), referer = "$mainUrl/"); if (resolved.isNotEmpty()) return resolved.map { it.copy(providerId = id) }
        }
        return getStreamsGateway(raw, episodeNumber)
    }

    private suspend fun requestDocument(url: String): Document? = withContext(Dispatchers.IO) { runCatching {
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140.0.0.0 Mobile Safari/537.36").header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8").header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7").header("Referer", "$mainUrl/").build()
        client.newCall(request).execute().use { response -> if (!response.isSuccessful) null else response.body?.string()?.takeIf { it.isNotBlank() }?.let { Jsoup.parse(it, response.request.url.toString()) } }
    }.getOrNull() }

    private suspend fun requestJson(url: String): JSONObject? = withContext(Dispatchers.IO) { runCatching {
        val request = Request.Builder().url(url).header("User-Agent", "KakaAnime/0.1").header("Accept", "application/json").build()
        client.newCall(request).execute().use { response -> if (!response.isSuccessful) null else response.body?.string()?.takeIf { it.isNotBlank() }?.let(::JSONObject) }
    }.getOrNull() }

    private fun Document.extractSearchResults(): List<ProviderAnime> {
        val cards = select("div.animepost, article.animpost, div.bs, div.bsx, div.listupd .bs, div.listupd .bsx, article.bs, article.bsx").mapNotNull { it.toProviderAnimeSearch() }.filter { it.title.isNotBlank() }.distinctBy { it.id }
        if (cards.isNotEmpty()) return cards
        return select("a[href*='/anime/']").mapNotNull { anchor ->
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val title = anchor.selectFirst(".tt, .title, h2, h3, h4")?.text()?.trim() ?: anchor.attr("title").trim().ifBlank { anchor.text().trim() }
            if (href.isBlank() || title.isBlank()) null else ProviderAnime("$id:${href.removeSuffix("/")}", title, id, posterUrl = anchor.selectFirst("img")?.let { it.absUrl("src").ifBlank { it.absUrl("data-src") } }?.ifBlank { null })
        }.distinctBy { it.id }
    }

    private fun Element.toProviderAnimeSearch(): ProviderAnime? {
        val link = selectFirst("div.title a, div.tt a, a[href*='/anime/'], a") ?: return null
        val title = selectFirst("div.title h2, div.tt h4, .tt, .title, h2, h3, h4")?.text()?.trim() ?: link.attr("title").trim().ifBlank { link.text().trim() }
        val href = link.absUrl("href").ifBlank { link.attr("href") }
        if (title.isBlank() || href.isBlank() || !href.contains("/anime/", true)) return null
        return ProviderAnime("$id:${href.removeSuffix("/")}", title, id, posterUrl = selectFirst("div.content-thumb img, div.limit img, img")?.let { it.absUrl("src").ifBlank { it.absUrl("data-src") } }?.ifBlank { null })
    }

    private fun Document.toProviderAnimeDetail(): ProviderAnime? {
        val title = selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val canonical = selectFirst("link[rel=canonical]")?.attr("href")?.ifBlank { null } ?: location()
        val slug = canonical.removeSuffix("/")
        val poster = selectFirst("div.thumb > img, div.fotoanime > img")?.let { it.absUrl("src").ifBlank { it.absUrl("data-src") } }
        val description = select("div.desc p, div.entry-content p, div.sinopc p").text().trim()
        val genres = select("div.genre-info > a, div.infozingle a[href*=/genre/]").map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
        val status = selectFirst("div.spe > span:contains(Status), div.infozingle > p:contains(Status)")?.text()?.substringAfter(":")?.trim().orEmpty().ifBlank { "UNKNOWN" }
        val rating = selectFirst("span.ratingValue, div.rating strong")?.text()?.replace("Rating", "", true)?.trim()?.toDoubleOrNull()
        val year = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(select("div.spe, div.infozingle").text())?.value?.toIntOrNull()
        val latest = select("div.lstepsiode.listeps ul li, div.listeps ul li, div.episodelist ul li").mapNotNull { it.toProviderEpisode(slug)?.number }.maxOrNull()
        return ProviderAnime("$id:$slug", title, id, posterUrl = poster?.ifBlank { null }, description = description, genres = genres, year = year, status = status, rating = rating, latestEpisode = latest)
    }

    private fun Element.toProviderEpisode(animeId: String): ProviderEpisode? {
        val anchor = selectFirst("span.lchx > a, a") ?: return null
        val title = anchor.text().trim().ifBlank { "Episode" }
        val number = Regex("(?:Episode|Ep|Eps)\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
            ?: Regex("(?:episode|ep)[^0-9]*([0-9]+)", RegexOption.IGNORE_CASE).find(anchor.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: selectFirst("span")?.text()?.filter { it.isDigit() }?.toIntOrNull() ?: return null
        val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }; if (href.isBlank()) return null
        return ProviderEpisode("$id:$href", "$id:${animeId.removePrefix("$id:")}", number, id, if (title.equals("Episode", true)) "Episode $number" else title)
    }

    private suspend fun searchGateway(query: String): List<ProviderAnime> = gatewayAnime("$primary?query=${encode(query)}", "$wajik/search?q=${encode(query)}")
    private suspend fun getAnimeGateway(slug: String): ProviderAnime? = gatewayAnime("$primary?query=${encode(slug)}", "$wajik/anime/${encodePath(slug)}").firstOrNull()
    private suspend fun gatewayAnime(vararg urls: String): List<ProviderAnime> { for (url in urls) { val root = requestJson(url) ?: continue; val list = extractAnimeArray(root).mapNotNull { it.toProviderAnime() }; if (list.isNotEmpty()) return list }; return emptyList() }

    private suspend fun getEpisodesGateway(slug: String): List<ProviderEpisode> {
        for (url in listOf("$primary?query=${encode(slug)}", "$wajik/anime/${encodePath(slug)}")) { val root = requestJson(url) ?: continue; val episodes = extractEpisodeArray(root).mapNotNull { item -> val number = item.episodeNumber() ?: return@mapNotNull null; val endpoint = item.firstString("slug", "endpoint", "id", "url") ?: number.toString(); ProviderEpisode("$id:$endpoint", "$id:$slug", number, id, item.firstString("title", "name") ?: "Episode $number", isNew = item.optBoolean("isNew", false)) }.distinctBy { it.number }.sortedBy { it.number }; if (episodes.isNotEmpty()) return episodes }
        return emptyList()
    }

    private suspend fun getStreamsGateway(slug: String, episodeNumber: Int): List<ProviderStream> {
        val episode = getEpisodesGateway(slug).firstOrNull { it.number == episodeNumber }; val endpoint = episode?.id?.removePrefix("$id:") ?: episodeNumber.toString()
        for (url in listOf("$primary?query=${encode(slug)}&episode=$episodeNumber", "$wajik/episode/${encodePath(endpoint)}", "$wajik/episode/$episodeNumber")) { val root = requestJson(url) ?: continue; val candidates = mutableListOf<ProviderStream>(); collectUrls(root, candidates); val unique = candidates.distinctBy { it.url }; if (unique.isEmpty()) continue; val resolved = resolver.resolve(unique.map { it.url }).map { it.copy(providerId = id) }; if (resolved.isNotEmpty()) return resolved }
        return emptyList()
    }

    private fun JSONObject.toProviderAnime(): ProviderAnime? { val title = firstString("title", "name", "animeTitle", "judul") ?: return null; val rawId = firstString("slug", "endpoint", "id", "animeId") ?: title.slugify(); val episodes = extractEpisodeArray(this); return ProviderAnime("$id:$rawId", title, id, posterUrl = firstString("poster", "posterUrl", "image", "thumbnail", "thumb"), description = firstString("description", "synopsis", "sinopsis") ?: "", genres = extractStringArray(this, "genres", "genre"), year = firstString("year", "released", "release")?.let { Regex("\\b(19\\d{2}|20\\d{2})\\b").find(it)?.value?.toIntOrNull() }, status = firstString("status") ?: "UNKNOWN", rating = firstString("rating", "score", "skor")?.toDoubleOrNull(), latestEpisode = episodes.mapNotNull { it.episodeNumber() }.maxOrNull()) }
    private fun extractAnimeArray(root: JSONObject): List<JSONObject> { for (key in listOf("results", "anime", "items", "search", "data")) root.optJSONArray(key)?.let { return it.objects() }; root.optJSONObject("data")?.let { nested -> extractAnimeArray(nested).takeIf { it.isNotEmpty() }?.let { return it } }; return if (root.has("title") || root.has("animeTitle") || root.has("judul")) listOf(root) else emptyList() }
    private fun extractEpisodeArray(root: JSONObject): List<JSONObject> { for (key in listOf("episodes", "episode", "episodeList", "episode_list", "episodeListData")) root.optJSONArray(key)?.let { return it.objects() }; root.optJSONObject("data")?.let { nested -> extractEpisodeArray(nested).takeIf { it.isNotEmpty() }?.let { return it } }; return emptyList() }
    private fun collectUrls(value: Any?, out: MutableList<ProviderStream>, quality: String? = null) { when (value) { is JSONObject -> { val keys = listOf("url", "file", "stream", "streamUrl", "stream_url", "m3u8", "mp4", "source", "videoUrl"); val q = value.firstString("quality", "resolution") ?: quality; for (key in keys) value.optString(key).trim().takeIf { it.startsWith("http", true) }?.let { out += stream(it, q) }; value.keys().forEachRemaining { key -> if (key !in keys) collectUrls(value.opt(key), out, q) } }; is JSONArray -> for (i in 0 until value.length()) collectUrls(value.opt(i), out, quality); is String -> if (value.startsWith("http", true)) out += stream(value, quality) } }
    private fun stream(url: String, quality: String?) = ProviderStream(id, url, quality?.ifBlank { null }, "Japanese", "Indonesian", when { url.contains(".m3u8", true) -> StreamType.HLS; url.contains(".mpd", true) -> StreamType.DASH; url.contains(".mp4", true) -> StreamType.MP4; else -> StreamType.UNKNOWN })
    private fun JSONObject.firstString(vararg keys: String): String? = keys.firstNotNullOfOrNull { key -> optString(key).trim().ifBlank { null } }
    private fun JSONObject.episodeNumber(): Int? = firstString("episode", "episodeNumber", "number", "episodeNum")?.toIntOrNull() ?: Regex("(?:episode|eps|ep)[^0-9]*(\\d+)", RegexOption.IGNORE_CASE).find(firstString("title", "name", "slug", "endpoint", "id", "judul").orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
    private fun extractStringArray(root: JSONObject, vararg keys: String): List<String> { for (key in keys) root.optJSONArray(key)?.let { a -> return buildList { for (i in 0 until a.length()) a.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add) } }; return emptyList() }
    private fun JSONArray.objects() = buildList<JSONObject> { for (i in 0 until length()) optJSONObject(i)?.let(::add) }
    private fun normalizeAnimeId(value: String): String { val raw = value.removePrefix("$id:").trim().trim('/'); return raw.substringAfterLast("/anime/", raw).substringBefore("?").trim('/') }
    private fun String.slugify() = trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    private fun encode(value: String) = URLEncoder.encode(value.trim(), "UTF-8")
    private fun encodePath(value: String) = value.trim('/').split('/').joinToString("/") { encode(it) }
}
