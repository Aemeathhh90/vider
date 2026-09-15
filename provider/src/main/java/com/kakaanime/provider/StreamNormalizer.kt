package com.kakaanime.provider

object StreamNormalizer {

    fun normalize(
        streams: List<ProviderStream>
    ): List<NormalizedStream> {

        return streams
            .mapNotNull { stream ->

                if (stream.url.isBlank()) {
                    return@mapNotNull null
                }

                NormalizedStream(
                    providerId = stream.providerId,
                    url = stream.url,
                    quality = detectQuality(stream.quality),
                    type = detectType(stream),
                    language = stream.language,
                    subtitleLanguage = stream.subtitleLanguage,
                    headers = stream.headers,
                    isPremium = detectPremium(stream.quality)
                )
            }
            .distinctBy {
                StreamKey(
                    url = it.url,
                    quality = it.quality,
                    subtitle = it.subtitleLanguage
                )
            }
            .sortedByDescending {
                it.quality.value
            }
    }

    private fun detectQuality(
        quality: String?
    ): StreamQuality {

        val value = quality?.lowercase() ?: return StreamQuality.UNKNOWN

        return when {
            value.contains("1080") ||
            value.contains("fullhd") ||
            value.contains("full hd") ->
                StreamQuality.Q1080

            value.contains("720") ||
            value.contains("hd") ->
                StreamQuality.Q720

            value.contains("480") ||
            value.contains("sd") ->
                StreamQuality.Q480

            value.contains("360") ->
                StreamQuality.Q360

            else ->
                StreamQuality.UNKNOWN
        }
    }

    private fun detectType(
        stream: ProviderStream
    ): StreamType {

        val url = stream.url.lowercase()

        return when {
            stream.type != StreamType.UNKNOWN ->
                stream.type

            url.contains(".m3u8") ->
                StreamType.HLS

            url.contains(".mpd") ->
                StreamType.DASH

            url.contains(".mp4") ->
                StreamType.MP4

            else ->
                StreamType.UNKNOWN
        }
    }

    private fun detectPremium(
        quality: String?
    ): Boolean {

        val value = quality?.lowercase() ?: return false
        return value.contains("1080")
    }

    private data class StreamKey(
        val url: String,
        val quality: StreamQuality,
        val subtitle: String?
    )
}
