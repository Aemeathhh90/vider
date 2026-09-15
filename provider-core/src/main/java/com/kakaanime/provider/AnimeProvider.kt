package com.kakaanime.provider

interface AnimeProvider {
    val id: String
    val name: String
    val priority: Int

    suspend fun search(query: String): List<ProviderAnime>
    suspend fun getAnime(animeId: String): ProviderAnime?
    suspend fun getEpisodes(animeId: String): List<ProviderEpisode>
    suspend fun getStreams(animeId: String, episodeNumber: Int): List<ProviderStream>
}
