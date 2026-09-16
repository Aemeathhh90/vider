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
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class OtakudesuProvider(browserResolver: BrowserStreamResolver? = null) : AnimeProvider {
    override val id = "otakudesu"
    override val name = "Otakudesu"
    override val priority = 10
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private val webSource = OtakudesuWebSource()
    private val streamResolver = StreamResolver(ExtractorRegistry(browserResolver = browserResolver), browserResolver = browserResolver)
    private val baseUrl = "https://qrtzanim.vercel.app/api"
    private val legacyUrl = "https://otakudesu-api-jade.vercel.app/api"
    private val communityUrl = "https://api.otakudesu.natee.my.id/api/v1/anime"

    override suspend fun search(query: String): List<ProviderAnime> {
        val normalized = query.trim(); if (normalized.isBlank()) return emptyList()
        val primary = requestJson("$baseUrl/search?q=${encode(normalized)}&page=1")?.optJSONObject("data")?.optJSONArray("results")?.toProviderAnimeList().orEmpty()
        if (primary.isNotEmpty()) return primary
        val legacy = requestJson("$legacyUrl/search/${encode(normalized)}")?.optJSONArray("search_results")?.toLegacyProviderAnimeList().orEmpty()
        if (legacy.isNotEmpty()) return legacy
        return webSearch(normalized)
    }

    override suspend fun getAnime(animeId: String): ProviderAnime? {
        val slug = normalizeAnimeSlug(animeId); val apiSlug = apiAnimeSlug(slug)
        val web = webSource.getAnime(slug)
        if (web != null) return ProviderAnime(id = "$id:$slug", title = web.title, providerId = id)
        val primary = requestJson("$baseUrl/anime/${encodePath(apiSlug)}")?.optJSONObject("data")
        if (primary != null) return primary.toProviderAnime(slug)
        val community = requestJson("$communityUrl/detail/${encodePath(apiSlug)}")?.optJSONObject("data")
        if (community != null) return community.toCommunityAnime(slug)
        return requestJson("$legacyUrl/anime/${encodePath(apiSlug)}")?.toLegacyProviderAnime(slug)
    }

    override suspend fun getEpisodes(animeId: String): List<ProviderEpisode> {
        val slug = normalizeAnimeSlug(animeId)
        val apiSlugs = listOf(apiAnimeSlug(slug), slug).distinct()
        val merged = linkedMapOf<Int, ProviderEpisode>()

        // Merge structured sources instead of stopping after the first non-empty
        // response. Some API variants expose only the newest episode range, while
        // the Otakudesu web page can contain older episodes such as Episode 7.
        for (apiSlug in apiSlugs) {
            val data = requestJson("$baseUrl/anime/${encodePath(apiSlug)}")?.optJSONObject("data") ?: continue
            data.episodeArrays().flatMap { it.toProviderEpisodeList(slug) }.forEach { merged.putIfAbsent(it.number, it) }
        }

        for (apiSlug in apiSlugs) {
            val data = requestJson("$communityUrl/detail/${encodePath(apiSlug)}")?.optJSONObject("data") ?: continue
            data.episodeArrays().flatMap { it.toCommunityProviderEpisodeList(slug) }.forEach { merged.putIfAbsent(it.number, it) }
        }

        // Always supplement structured results with the live web page. This is
        // important for long-running anime where an API may intentionally expose
        // only a recent subset of the full episode history.
        webSource.getAnime(slug)?.let { web ->
            webSource.getEpisodes(web).forEach { episode ->
                merged.putIfAbsent(episode.number, ProviderEpisode("$id:${episode.url}", "$id:$slug", episode.number, id, episode.title))
            }
        }
        webSource.getEpisodes(slug).forEach { episode ->
            merged.putIfAbsent(episode.number, ProviderEpisode("$id:${episode.url}", "$id:$slug", episode.number, id, episode.title))
        }

        // Final legacy structured supplement.
        for (apiSlug in apiSlugs) {
            requestJson("$legacyUrl/anime/${encodePath(apiSlug)}")?.let { it.optJSONObject("anime_detail") ?: it }
                ?.optJSONArray("episode_list")?.toLegacyProviderEpisodeList(slug).orEmpty()
                .forEach { merged.putIfAbsent(it.number, it) }
        }
        return merged.values.sortedBy { it.number }
    }

    override suspend fun getStreams(animeId: String, episodeNumber: Int): List<ProviderStream> {
        val episode = getEpisodes(animeId).firstOrNull { it.number == episodeNumber } ?: return emptyList()
        val episodeRef = episode.id.removePrefix("$id:")

        if (episodeRef.startsWith("community:")) {
            val episodeSlug = episodeRef.removePrefix("community:").trim('/')
            val data = requestJson("$communityUrl/episode/${encodePath(episodeSlug)}")?.optJSONObject("data")
            if (data != null) {
                val candidates = buildList {
                    data.optString("stream_url").trim().takeIf { it.isNotBlank() }?.let(::add)
                    val mirrors = data.optJSONArray("mirrors") ?: JSONArray()
                    for (i in 0 until mirrors.length()) {
                        mirrors.optJSONObject(i)?.optString("content")?.trim()?.takeIf { it.isNotBlank() }?.let { token ->
                            requestJson("$communityUrl/mirror?content=${encode(token)}")?.optJSONObject("data")?.optString("url")?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.distinct()
                val resolved = streamResolver.resolve(candidates, referer = "https://otakudesu.blog/")
                if (resolved.isNotEmpty()) return resolved.map { it.copy(providerId = id) }
            }
        }

        if (episodeRef.startsWith("http", true)) {
            val page = webSource.getEpisodePage(OtakudesuWebSource.WebEpisode(episodeRef, episodeNumber, episode.title ?: "Episode $episodeNumber"))
            val discovered = page?.let { webSource.discoverPlaybackUrls(it, episodeRef) }.orEmpty()
            val candidates = (listOf(episodeRef) + discovered).distinct()
            val resolved = streamResolver.resolve(candidates, referer = episodeRef)
            if (resolved.isNotEmpty()) return resolved.map { it.copy(providerId = id) }
        }

        val episodeSlug = normalizeEpisodeSlug(episodeRef)
        for (candidateUrl in listOf("$baseUrl/episode/${encodePath(episodeSlug)}", "$baseUrl/streams/${encodePath(episodeSlug)}")) {
            val primary = requestJson(candidateUrl) ?: continue
            val data = primary.optJSONObject("data")
            val candidates = data?.streamCandidates().orEmpty()
            val resolved = streamResolver.resolve(candidates, referer = episodeRef)
            if (resolved.isNotEmpty()) return resolved.map { it.copy(providerId = id) }
        }
        val legacy = requestJson("$legacyUrl/episode/${encodePath(episodeSlug)}")
        val streamUrl = legacy?.let { it.optJSONObject("episode_detail") ?: it }?.optString("stream_link")?.trim().orEmpty()
        if (streamUrl.isNotBlank()) {
            val resolved = streamResolver.resolve(listOf(streamUrl), referer = episodeRef)
            if (resolved.isNotEmpty()) return resolved.map { it.copy(providerId = id) }
        }
        return emptyList()
    }

    private suspend fun webSearch(query: String): List<ProviderAnime> = withContext(Dispatchers.IO) { runCatching { val results = linkedMapOf<String, ProviderAnime>(); val encoded = encode(query); val sources = listOf("https://otakudesu.blog", "https://otakudesu.ro", "https://otakudesu.cloud", "https://otakudesu.fit"); for (base in sources) { val url = "$base/?s=$encoded&post_type=anime"; val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36").header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8").build(); client.newCall(request).execute().use { response -> if (!response.isSuccessful) return@use; val html = response.body?.string().orEmpty(); val pattern = Regex("<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)); for (match in pattern.findAll(html)) { val href = decodeHtml(match.groupValues[1].trim()); val title = match.groupValues[2].replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim(); if (title.isBlank() || !href.contains("/anime/", true) && !href.contains("/series/", true)) continue; val slug = normalizeAnimeSlug(URI(base).resolve(href).toString()); if (slug.isBlank()) continue; results.putIfAbsent(slug.lowercase(), ProviderAnime("$id:$slug", title, id)) } }; if (results.isNotEmpty()) break }; results.values.toList() }.getOrDefault(emptyList()) }
    private suspend fun requestJson(url: String): JSONObject? = withContext(Dispatchers.IO) { runCatching { Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36").header("Accept", "application/json,text/plain,*/*").header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8").build().let { request -> client.newCall(request).execute().use { response -> if (!response.isSuccessful) null else response.body?.string()?.takeIf { it.isNotBlank() }?.let(::JSONObject) } } }.getOrNull() }
    private fun JSONArray.toProviderAnimeList() = buildList { for (i in 0 until length()) { val item = optJSONObject(i) ?: continue; val slug = normalizeAnimeSlug(item.optString("slug")); if (slug.isNotBlank()) add(ProviderAnime("$id:$slug", item.optString("title").ifBlank { "Unknown Anime" }, id, posterUrl = item.optString("thumbnail").ifBlank { null }, status = item.optString("status").ifBlank { "UNKNOWN" }, rating = item.optString("score").toDoubleOrNull())) } }
    private fun JSONArray.toLegacyProviderAnimeList() = buildList { for (i in 0 until length()) { val item = optJSONObject(i) ?: continue; val slug = normalizeAnimeSlug(item.optString("endpoint")); if (slug.isNotBlank()) add(ProviderAnime("$id:$slug", item.optString("title").ifBlank { "Unknown Anime" }, id, posterUrl = item.optString("thumb").ifBlank { null }, status = item.optString("status").ifBlank { "UNKNOWN" }, rating = item.optString("rating").toDoubleOrNull())) } }
    private fun JSONObject.toProviderAnime(slug: String) = ProviderAnime("$id:$slug", optString("title").ifBlank { "Unknown Anime" }, id, posterUrl = optString("thumbnail").ifBlank { null }, description = optString("synopsis"), status = optJSONObject("metadata")?.optString("status").orEmpty().ifBlank { "UNKNOWN" })
    private fun JSONObject.toCommunityAnime(slug: String) = ProviderAnime("$id:$slug", optString("title").ifBlank { "Unknown Anime" }, id, posterUrl = optString("poster").ifBlank { null }, description = optString("synopsis"), status = optString("status").ifBlank { "UNKNOWN" })
    private fun JSONObject.toLegacyProviderAnime(slug: String): ProviderAnime { val detail = optJSONObject("anime_detail") ?: this; return ProviderAnime("$id:$slug", detail.optString("title").ifBlank { "Unknown Anime" }, id, posterUrl = detail.optString("thumb").ifBlank { null }, description = detail.optString("synopsis"), status = detail.optString("status").ifBlank { "UNKNOWN" }) }

    private fun JSONObject.episodeArrays(): List<JSONArray> = listOf("episodeList", "episode_list", "episodes", "episodeLists").mapNotNull { optJSONArray(it) }
    private fun JSONArray.toCommunityProviderEpisodeList(slug: String) = buildList { for (i in 0 until length()) { val item = optJSONObject(i) ?: continue; val endpoint = item.optString("slug").trim('/'); val number = item.optInt("episode_number", -1).takeIf { it >= 0 } ?: extractEpisodeNumber(item.optString("episode"), endpoint) ?: continue; if (endpoint.isBlank()) continue; add(ProviderEpisode("$id:community:$endpoint", "$id:$slug", number, id, item.optString("episode").ifBlank { "Episode $number" })) } }.sortedBy { it.number }
    private fun JSONArray.toProviderEpisodeList(slug: String) = buildList { for (i in 0 until length()) { val item = optJSONObject(i) ?: continue; val endpoint = item.optString("slug").trim('/'); val rawNumber = item.optString("episode").trim(); val number = rawNumber.toIntOrNull() ?: item.optInt("episode_number", -1).takeIf { it >= 0 } ?: extractEpisodeNumber(item.optString("title"), endpoint) ?: continue; if (endpoint.isBlank()) continue; add(ProviderEpisode("$id:$endpoint", "$id:$slug", number, id, item.optString("title").ifBlank { "Episode $number" }, item.optString("thumbnail").ifBlank { null })) } }.sortedBy { it.number }
    private fun JSONArray.toLegacyProviderEpisodeList(slug: String) = buildList { for (i in 0 until length()) { val item = optJSONObject(i) ?: continue; val endpoint = item.optString("endpoint").trim('/'); val number = extractEpisodeNumber(item.optString("title"), endpoint) ?: continue; add(ProviderEpisode("$id:$endpoint", "$id:$slug", number, id, item.optString("title").ifBlank { "Episode $number" }, item.optString("thumb").ifBlank { null })) } }.sortedBy { it.number }
    private fun JSONObject.streamCandidates() = buildList { val streams = optJSONArray("streams") ?: JSONArray(); for (i in 0 until streams.length()) streams.optJSONObject(i)?.optString("embedUrl")?.trim()?.takeIf { it.isNotBlank() }?.let(::add); optString("defaultStreamUrl").trim().takeIf { it.isNotBlank() }?.let(::add) }.distinct()
    private fun normalizeAnimeSlug(value: String): String { val raw = value.removePrefix("$id:").trim().trim('/'); val slug = raw.substringAfterLast("/anime/", raw).substringAfterLast("/series/", raw).substringBefore("?").trim('/'); return when { slug.equals("1piece-sub-indo", true) -> "1piece-sub-indo"; slug.equals("onepiece-sub-indo", true) -> "1piece-sub-indo"; else -> slug } }
    private fun apiAnimeSlug(slug: String): String = when { slug.equals("1piece-sub-indo", true) -> "one-piece-sub-indo"; else -> slug }
    private fun normalizeEpisodeSlug(value: String): String = value.removePrefix("$id:").substringAfter("/episode/", value.removePrefix("$id:")).substringBefore("?").trim('/')
    private fun extractEpisodeNumber(title: String, slug: String): Int? = Regex("(?:episode|eps|ep)[^0-9]*(\\d+)", RegexOption.IGNORE_CASE).find("$title $slug")?.groupValues?.getOrNull(1)?.toIntOrNull()
    private fun encode(value: String) = URLEncoder.encode(value.trim(), "UTF-8")
    private fun encodePath(value: String) = value.trim('/').split('/').joinToString("/") { encode(it) }
    private fun decodeHtml(value: String): String = value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
}
