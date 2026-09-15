package com.kakaanime.provider

class ProviderEngine(private val registry: ProviderRegistry) {
    private val router = SmartProviderRouter(registry)

    suspend fun search(query: String): List<ProviderAnime> = router.search(query)
    suspend fun getAnime(animeId: String): ProviderAnime? = router.getAnime(animeId)
    suspend fun getEpisodes(animeId: String): List<ProviderEpisode> = router.getEpisodes(animeId)

    suspend fun getEpisodeStreams(animeId: String, episodeNumber: Int): NormalizedEpisodeStream {
        val rawStreams = router.getStreams(animeId, episodeNumber)
        val priorities = registry.all().associate { it.id to it.priority }
        val normalized = StreamNormalizer.normalize(
            ProviderStreamDeduplicator.deduplicate(rawStreams, priorities)
        )
        println("STREAM_CCTV_ENGINE_STREAMS animeId=$animeId episode=$episodeNumber raw=${rawStreams.size} normalized=${normalized.size}")
        return NormalizedEpisodeStream(animeId, episodeNumber, normalized)
    }

    suspend fun getBestStream(
        animeId: String,
        episodeNumber: Int,
        preferredQuality: StreamQuality? = null,
        premium: Boolean = false
    ): NormalizedStream? = StreamSelector.best(
        getEpisodeStreams(animeId, episodeNumber).streams,
        preferredQuality,
        premium
    )
}
