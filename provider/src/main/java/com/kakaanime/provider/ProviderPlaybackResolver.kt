package com.kakaanime.provider

import com.kakaanime.provider.extractor.BrowserStreamResolver

/** Resolves playable streams and provider episode lists without exposing provider details to the UI. */
object ProviderPlaybackResolver {
    suspend fun resolve(
        title: String,
        episodeNumber: Int,
        premium: Boolean,
        preferredQuality: StreamQuality? = null,
        seasonNumber: Int? = null,
        seasonTitle: String? = null,
        browserResolver: BrowserStreamResolver? = null,
    ): NormalizedStream? {
        val candidates = findCandidates(title, seasonNumber, seasonTitle, browserResolver)
        if (candidates.isEmpty()) return null
        val ordered = candidates.sortedWith(compareBy<ProviderAnime> { it.providerId.isBlank() }.thenByDescending { it.latestEpisode ?: 0 })
        val engine = ProviderFactory.createEngine(browserResolver)
        for (candidate in ordered) {
            val stream = runCatching { engine.getBestStream(candidate.id, episodeNumber, preferredQuality, premium) }.getOrNull()
            if (stream != null && stream.url.isNotBlank()) return stream
        }
        return null
    }

    suspend fun episodes(
        title: String,
        seasonNumber: Int? = null,
        seasonTitle: String? = null,
        browserResolver: BrowserStreamResolver? = null,
    ): List<ProviderEpisode> {
        val candidates = findCandidates(title, seasonNumber, seasonTitle, browserResolver)
        if (candidates.isEmpty()) return emptyList()
        val ordered = candidates.sortedWith(compareBy<ProviderAnime> { it.providerId.isBlank() }.thenByDescending { it.latestEpisode ?: 0 })
        val engine = ProviderFactory.createEngine(browserResolver)
        for (candidate in ordered) {
            val episodes = runCatching { engine.getEpisodes(candidate.id) }.getOrDefault(emptyList())
                .filter { it.number > 0 }.distinctBy { it.number }.sortedByDescending { it.number }
            if (episodes.isNotEmpty()) return episodes
        }
        return emptyList()
    }

    private suspend fun findCandidates(
        title: String,
        seasonNumber: Int?,
        seasonTitle: String?,
        browserResolver: BrowserStreamResolver?,
    ): List<ProviderAnime> {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) return emptyList()
        val queries = buildList {
            if (seasonNumber != null) {
                add("$normalizedTitle Season $seasonNumber")
                add("$normalizedTitle S$seasonNumber")
            }
            seasonTitle?.trim()?.takeIf { it.isNotBlank() }?.let { add("$normalizedTitle $it") }
            add(normalizedTitle)
        }.distinct()
        val engine = ProviderFactory.createEngine(browserResolver)
        for (query in queries) {
            val results = runCatching { engine.search(query) }.getOrDefault(emptyList())
            if (results.isEmpty()) continue
            if (seasonNumber == null) return results
            val matching = results.filter { candidate ->
                candidate.seasonNumber == seasonNumber || SeasonIdentityParser.parse(candidate.title, candidate.id).seasonNumber == seasonNumber
            }
            if (matching.isNotEmpty()) return matching
        }
        return emptyList()
    }
}

data class SeasonIdentity(val animeGroupId: String, val seasonNumber: Int?, val seasonTitle: String?, val searchAliases: List<String>)

object SeasonIdentityParser {
    private val seasonPattern = Regex("(?:\\bseason\\s*(\\d+)\\b|\\bs(?:eason)?\\s*(\\d+)\\b)", RegexOption.IGNORE_CASE)

    fun parse(title: String, slug: String = ""): SeasonIdentity {
        val source = "$title $slug"
        val match = seasonPattern.find(source)
        val number = match?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.toIntOrNull()
        val normalizedTitle = title.trim().replace(Regex("\\s+"), " ")
        val groupTitle = normalizedTitle
            .replace(Regex("\\s*[-:]?\\s*season\\s*\\d+\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[-:]?\\s*s(?:eason)?\\s*\\d+\\b", RegexOption.IGNORE_CASE), "")
            .trim().ifBlank { normalizedTitle }
        val groupId = groupTitle.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "unknown" }
        val aliases = buildList {
            add(groupTitle)
            if (number != null) {
                add("$groupTitle Season $number")
                add("$groupTitle S$number")
            }
            add(normalizedTitle)
        }.distinct()
        return SeasonIdentity(groupId, number, number?.let { "Season $it" }, aliases)
    }
}
