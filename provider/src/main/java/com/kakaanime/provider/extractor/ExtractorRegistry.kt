package com.kakaanime.provider.extractor

import com.kakaanime.provider.extractor.extractors.GenericDirectExtractor

class ExtractorRegistry(
    extractors: List<StreamExtractor> = emptyList()
) {
    private val extractors: List<StreamExtractor> =
        (extractors + GenericDirectExtractor())
            .distinctBy { it.id }
            .sortedByDescending { it.priority }

    fun find(url: String): List<StreamExtractor> = extractors.filter {
        runCatching { it.canHandle(url) }.getOrDefault(false)
    }
}
