package com.kakaanime.provider.extractor

import com.kakaanime.provider.extractor.extractors.GenericDirectExtractor
import com.kakaanime.provider.extractor.extractors.GenericEmbedExtractor
import com.kakaanime.provider.extractor.extractors.JavascriptMediaExtractor
import com.kakaanime.provider.extractor.extractors.KrakenFilesExtractor
import com.kakaanime.provider.extractor.extractors.OtakudesuHostExtractor
import com.kakaanime.provider.extractor.extractors.OtakudesuServerExtractor
import com.kakaanime.provider.extractor.extractors.PixelDrainExtractor
import com.kakaanime.provider.extractor.extractors.SamehadakuEpisodeExtractor

class ExtractorRegistry(
    customExtractors: List<StreamExtractor> = emptyList(),
    includeSamehadakuEpisodeExtractor: Boolean = true,
    private val browserResolver: BrowserStreamResolver? = null
) {
    private val extractors: List<StreamExtractor> = buildList {
        if (includeSamehadakuEpisodeExtractor) add(SamehadakuEpisodeExtractor(browserResolver))
        add(OtakudesuHostExtractor())
        addAll(
            listOf(
                OtakudesuServerExtractor(),
                KrakenFilesExtractor(),
                PixelDrainExtractor(),
                JavascriptMediaExtractor()
            )
        )
        addAll(customExtractors)
        val specific = distinctBy { it.id }.sortedByDescending { it.priority }
        clear()
        addAll(specific)
        add(GenericEmbedExtractor())
        add(GenericDirectExtractor())
    }.distinctBy { it.id }

    fun find(url: String): List<StreamExtractor> = extractors.filter {
        runCatching { it.canHandle(url) }.getOrDefault(false)
    }
}
