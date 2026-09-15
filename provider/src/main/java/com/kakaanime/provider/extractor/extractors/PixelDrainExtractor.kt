package com.kakaanime.provider.extractor.extractors

import com.kakaanime.provider.ProviderStream
import com.kakaanime.provider.StreamType
import com.kakaanime.provider.extractor.StreamExtractor
import java.net.URI

class PixelDrainExtractor : StreamExtractor {
    override val id = "pixeldrain"
    override val priority = 84
    override fun canHandle(url: String): Boolean = runCatching { URI(url).host.orEmpty().contains("pixeldrain.com", true) }.getOrDefault(false)
    override suspend fun extract(url: String, referer: String?): List<ProviderStream> {
        val value = url.trim()
        val id = Regex("/u/([\\w-]+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)
            ?: Regex("/api/file/([\\w-]+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)
            ?: return emptyList()
        return listOf(ProviderStream(this.id, "https://pixeldrain.com/api/file/$id", type = StreamType.MP4,
            headers = referer?.takeIf { it.isNotBlank() }?.let { mapOf("Referer" to it) }.orEmpty()))
    }
}
