package com.kakaanime.provider

data class ProviderStream(
    val providerId: String,
    val url: String,
    val quality: String? = null,
    val language: String? = null,
    val subtitleLanguage: String? = null,
    val type: StreamType = StreamType.UNKNOWN,
    val headers: Map<String, String> = emptyMap()
)

enum class StreamType {
    HLS,
    DASH,
    MP4,
    UNKNOWN
}
