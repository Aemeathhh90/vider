package com.kakaanime.provider

class ProviderEngine(
    private val registry: ProviderRegistry
) {
    private val router = SmartProviderRouter(registry)

    suspend fun search(query: String): List<ProviderAnime> = router.search(query)

    suspend fun getAnime(animeId: String): ProviderAnime? = router.getAnime(animeId)

    suspend fun getEpisodes(animeId: String): List<ProviderEpisode> = router.getEpisodes(animeId)

    suspend fun getEpisodeStreams(animeId: String, episodeNumber: Int): NormalizedEpisodeStream {
        val raw = router.getStreams(animeId, episodeNumber)
        val priorities = registry.all().associate { it.id to it.priority }
        val deduplicated = ProviderStreamDeduplicator.deduplicate(raw, priorities)
        return NormalizedEpisodeStream(
            animeId = animeId,
            episodeNumber = episodeNumber,
            streams = StreamNormalizer.normalize(deduplicated)
        )
    }

    suspend fun getBestStream(
        animeId: String,
        episodeNumber: Int,
        preferredQuality: StreamQuality? = null,
        premium: Boolean = false
    ): NormalizedStream? = StreamSelector.best(
        streams = getEpisodeStreams(animeId, episodeNumber).streams,
        preferredQuality = preferredQuality,
        premium = premium
    )
}
