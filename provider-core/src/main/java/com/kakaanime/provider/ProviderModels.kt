package com.kakaanime.provider

data class ProviderAnime(
    val id: String,
    val title: String,
    val providerId: String = "",
    val alternativeTitles: List<String> = emptyList(),
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val description: String = "",
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val status: String = "UNKNOWN",
    val rating: Double? = null,
    val latestEpisode: Int? = null,
    val animeGroupId: String = id,
    val seasonNumber: Int? = null,
    val seasonTitle: String? = null,
    val searchAliases: List<String> = emptyList(),
)

data class ProviderEpisode(
    val id: String,
    val animeId: String,
    val number: Int,
    val providerId: String = "",
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val isNew: Boolean = false,
    val releasedAt: Long? = null,
    val animeGroupId: String = animeId,
    val seasonNumber: Int? = null,
    val seasonTitle: String? = null,
    val availability: EpisodeAvailability = EpisodeAvailability.AVAILABLE,
)

enum class EpisodeAvailability { AVAILABLE, NOT_AVAILABLE, NOT_RELEASED }

data class ProviderStream(
    val providerId: String,
    val url: String,
    val quality: String? = null,
    val language: String? = null,
    val subtitleLanguage: String? = null,
    val type: StreamType = StreamType.UNKNOWN,
    val headers: Map<String, String> = emptyMap(),
)

enum class StreamType { HLS, DASH, MP4, UNKNOWN }
