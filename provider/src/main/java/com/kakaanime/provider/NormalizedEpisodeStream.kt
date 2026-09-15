package com.kakaanime.provider

data class NormalizedEpisodeStream(
    val animeId: String,
    val episodeNumber: Int,
    val streams: List<NormalizedStream>
) {
    val bestStream: NormalizedStream?
        get() = StreamSelector.best(streams = streams)
}
