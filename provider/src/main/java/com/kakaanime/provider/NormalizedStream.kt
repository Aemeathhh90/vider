package com.kakaanime.provider

import java.util.concurrent.ConcurrentHashMap

data class NormalizedStream(
    val providerId: String,
    val url: String,
    val quality: StreamQuality = StreamQuality.UNKNOWN,
    val type: StreamType = StreamType.UNKNOWN,
    val language: String? = null,
    val subtitleLanguage: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val isPremium: Boolean = false
) {
    init { StreamMetadataCache.remember(this) }
}

object StreamMetadataCache {
    private const val MAX_ENTRIES = 128
    private val streams = ConcurrentHashMap<String, NormalizedStream>()

    fun remember(stream: NormalizedStream) {
        val url = stream.url.trim()
        if (url.isBlank()) return
        streams[url] = stream
        if (streams.size > MAX_ENTRIES) streams.keys.firstOrNull()?.let(streams::remove)
    }

    fun find(url: String): NormalizedStream? = streams[url.trim()]
}

enum class StreamQuality(val value: Int) {
    Q360(360), Q480(480), Q720(720), Q1080(1080), UNKNOWN(0)
}
