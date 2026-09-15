package com.kakaanime.provider

object StreamNormalizer {
    fun normalize(streams: List<ProviderStream>): List<NormalizedStream> = streams
        .mapNotNull { stream ->
            if (stream.url.isBlank()) return@mapNotNull null
            NormalizedStream(
                providerId = stream.providerId,
                url = stream.url,
                quality = detectQuality(stream.quality),
                type = detectType(stream),
                language = stream.language,
                subtitleLanguage = stream.subtitleLanguage,
                headers = stream.headers,
                isPremium = detectPremium(stream.quality),
            )
        }
        .distinctBy { Triple(it.url, it.quality, it.subtitleLanguage) }
        .sortedByDescending { it.quality.value }

    private fun detectQuality(quality: String?): StreamQuality {
        val value = quality?.lowercase() ?: return StreamQuality.UNKNOWN
        return when {
            value.contains("1080") || value.contains("fullhd") || value.contains("full hd") -> StreamQuality.Q1080
            value.contains("720") || value.contains("hd") -> StreamQuality.Q720
            value.contains("480") || value.contains("sd") -> StreamQuality.Q480
            value.contains("360") -> StreamQuality.Q360
            else -> StreamQuality.UNKNOWN
        }
    }

    private fun detectType(stream: ProviderStream): StreamType {
        val url = stream.url.lowercase()
        return when {
            stream.type != StreamType.UNKNOWN -> stream.type
            ".m3u8" in url -> StreamType.HLS
            ".mpd" in url -> StreamType.DASH
            ".mp4" in url -> StreamType.MP4
            else -> StreamType.UNKNOWN
        }
    }

    private fun detectPremium(quality: String?): Boolean = quality?.lowercase()?.contains("1080") == true
}
