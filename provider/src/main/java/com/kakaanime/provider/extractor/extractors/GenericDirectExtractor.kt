package com.kakaanime.provider.extractor.extractors

import com.kakaanime.provider.ProviderStream
import com.kakaanime.provider.StreamType
import com.kakaanime.provider.extractor.StreamExtractor

class GenericDirectExtractor : StreamExtractor {
    override val id = "generic-direct"
    override val priority = 0

    override fun canHandle(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return clean.endsWith(".m3u8") || clean.endsWith(".mpd") || clean.endsWith(".mp4") || clean.endsWith(".mkv") || clean.endsWith(".webm")
    }

    override suspend fun extract(url: String, referer: String?): List<ProviderStream> {
        if (!canHandle(url)) return emptyList()
        val clean = url.substringBefore('?').lowercase()
        val type = when {
            clean.endsWith(".m3u8") -> StreamType.HLS
            clean.endsWith(".mpd") -> StreamType.DASH
            else -> StreamType.MP4
        }
        return listOf(ProviderStream("resolver", url, type = type, headers = referer?.let { mapOf("Referer" to it) }.orEmpty()))
    }
}
