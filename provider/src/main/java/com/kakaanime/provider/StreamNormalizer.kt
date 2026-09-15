package com.kakaanime.provider

object StreamNormalizer {
    fun normalize(streams: List<ProviderStream>): List<NormalizedStream> = streams.mapNotNull { stream ->
        if (stream.url.isBlank()) return@mapNotNull null
        NormalizedStream(
            providerId = stream.providerId,
            url = stream.url,
            quality = detectQuality(stream.quality),
            type = detectType(stream),
            language = stream.language,
            subtitleLanguage = stream.subtitleLanguage,
            headers = stream.headers,
            isPremium = stream.quality.orEmpty().contains("1080", true)
        )
    }.distinctBy { Triple(it.url, it.quality, it.subtitleLanguage) }
        .sortedByDescending { it.quality.value }

    private fun detectQuality(quality: String?): StreamQuality = when {
        quality?.contains("1080", true) == true || quality?.contains("fullhd", true) == true -> StreamQuality.Q1080
        quality?.contains("720", true) == true || quality?.contains("hd", true) == true -> StreamQuality.Q720
        quality?.contains("480", true) == true || quality?.contains("sd", true) == true -> StreamQuality.Q480
        quality?.contains("360", true) == true -> StreamQuality.Q360
        else -> StreamQuality.UNKNOWN
    }

    private fun detectType(stream: ProviderStream): StreamType {
        if (stream.type != StreamType.UNKNOWN) return stream.type
        val url = stream.url.lowercase()
        return when {
            ".m3u8" in url -> StreamType.HLS
            ".mpd" in url -> StreamType.DASH
            ".mp4" in url -> StreamType.MP4
            else -> StreamType.UNKNOWN
        }
    }
}
