package com.kakaanime.provider

/**
 * Baseline router. Provider calls remain sequential during migration so
 * behavior stays equivalent to the proven app implementation.
 */
class SmartProviderRouter(
    private val registry: ProviderRegistry
) {
    suspend fun search(query: String): List<ProviderAnime> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<ProviderAnime>()
        for (provider in registry.all()) {
            runCatching { provider.search(query) }
                .getOrDefault(emptyList())
                .let(results::addAll)
        }
        return results.distinctBy { "${it.providerId}:${it.id}" }
    }

    suspend fun getAnime(animeId: String): ProviderAnime? {
        for (provider in registry.all()) {
            val result = runCatching { provider.getAnime(animeId) }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    suspend fun getEpisodes(animeId: String): List<ProviderEpisode> {
        for (provider in registry.all()) {
            val result = runCatching { provider.getEpisodes(animeId) }.getOrDefault(emptyList())
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    suspend fun getStreams(animeId: String, episodeNumber: Int): List<ProviderStream> {
        val streams = mutableListOf<ProviderStream>()
        for (provider in registry.all()) {
            runCatching { provider.getStreams(animeId, episodeNumber) }
                .getOrDefault(emptyList())
                .let(streams::addAll)
        }
        return streams
    }
}
