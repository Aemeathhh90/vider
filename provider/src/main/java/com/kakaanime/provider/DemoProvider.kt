package com.kakaanime.provider

class DemoProvider : AnimeProvider {
    override val id = "demo"
    override val name = "KakaAnime Demo Provider"
    override val priority = 1

    private val demoAnime = ProviderAnime(
        id = "demo-one-piece",
        title = "One Piece",
        alternativeTitles = listOf("One Piece", "OP"),
        description = "Demo anime untuk menguji Provider Engine KakaAnime.",
        genres = listOf("Action", "Adventure", "Fantasy"),
        year = 1999,
        status = "ONGOING",
        rating = 9.0,
        latestEpisode = 10
    )

    override suspend fun search(query: String): List<ProviderAnime> {
        if (query.isBlank()) return listOf(demoAnime)
        return if (demoAnime.title.contains(query, ignoreCase = true)) listOf(demoAnime) else emptyList()
    }

    override suspend fun getAnime(animeId: String): ProviderAnime? =
        if (animeId == demoAnime.id) demoAnime else null

    override suspend fun getEpisodes(animeId: String): List<ProviderEpisode> {
        if (animeId != demoAnime.id) return emptyList()
        return (10 downTo 1).map { number ->
            ProviderEpisode(
                id = "${demoAnime.id}-$number",
                animeId = demoAnime.id,
                number = number,
                title = "Episode $number",
                isNew = number == 10
            )
        }
    }

    override suspend fun getStreams(animeId: String, episodeNumber: Int): List<ProviderStream> {
        if (animeId != demoAnime.id) return emptyList()
        return listOf(
            ProviderStream(
                providerId = id,
                url = "https://media.w3.org/2010/05/sintel/trailer.mp4",
                quality = "480p",
                language = "Japanese",
                subtitleLanguage = "Indonesian",
                type = StreamType.MP4
            ),
            ProviderStream(
                providerId = id,
                url = "https://media.w3.org/2010/05/bunny/trailer.mp4",
                quality = "720p",
                language = "Japanese",
                subtitleLanguage = "Indonesian",
                type = StreamType.MP4
            ),
            ProviderStream(
                providerId = id,
                url = "https://media.w3.org/2010/05/bunny/trailer.mp4",
                quality = "1080p",
                language = "Japanese",
                subtitleLanguage = "Indonesian",
                type = StreamType.MP4
            )
        )
    }
}
