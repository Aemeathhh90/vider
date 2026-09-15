package com.kakaanime.provider.extractor

import com.kakaanime.provider.extractor.extractors.GenericDirectExtractor
import com.kakaanime.provider.extractor.extractors.GenericEmbedExtractor
import com.kakaanime.provider.extractor.extractors.KrakenFilesExtractor
import com.kakaanime.provider.extractor.extractors.OtakudesuHostExtractor
import com.kakaanime.provider.extractor.extractors.OtakudesuServerExtractor
import com.kakaanime.provider.extractor.extractors.PixelDrainExtractor

class ExtractorRegistry(
    customExtractors: List<StreamExtractor> = emptyList()
) {
    private val extractors: List<StreamExtractor> =
        (customExtractors +
            listOf(
                OtakudesuHostExtractor(),
                OtakudesuServerExtractor(),
                KrakenFilesExtractor(),
                PixelDrainExtractor(),
                GenericEmbedExtractor(),
                GenericDirectExtractor()
            ))
            .distinctBy { it.id }
            .sortedByDescending { it.priority }

    fun find(url: String): List<StreamExtractor> = extractors.filter {
        runCatching { it.canHandle(url) }.getOrDefault(false)
    }
}
