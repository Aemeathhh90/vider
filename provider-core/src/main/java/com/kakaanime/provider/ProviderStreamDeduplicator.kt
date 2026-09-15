package com.kakaanime.provider

import java.net.URI

object ProviderStreamDeduplicator {
    fun deduplicate(streams: List<ProviderStream>, providerPriorities: Map<String, Int> = emptyMap()): List<ProviderStream> =
        streams.filter { it.url.isNotBlank() }
            .groupBy { canonicalUrl(it.url) }
            .values
            .map { candidates ->
                candidates.minWithOrNull(
                    compareBy<ProviderStream> { providerPriorities[it.providerId] ?: Int.MAX_VALUE }
                        .thenByDescending { qualityRank(it.quality) }
                        .thenByDescending { it.headers.size }
                ) ?: candidates.first()
            }
            .sortedWith(
                compareByDescending<ProviderStream> { qualityRank(it.quality) }
                    .thenBy { providerPriorities[it.providerId] ?: Int.MAX_VALUE }
                    .thenBy { it.providerId }
            )

    private fun canonicalUrl(raw: String): String = runCatching {
        val uri = URI(raw.trim())
        URI(uri.scheme?.lowercase(), uri.userInfo, uri.host?.lowercase(), uri.port,
            uri.path?.trimEnd('/'), uri.query, null).toString()
    }.getOrElse { raw.trim().trimEnd('/') }

    private fun qualityRank(quality: String?): Int {
        val value = quality.orEmpty().lowercase()
        return when {
            "2160" in value || "4k" in value -> 2160
            "1440" in value || "2k" in value -> 1440
            "1080" in value || "fhd" in value -> 1080
            "720" in value || "hd" in value -> 720
            "480" in value -> 480
            "360" in value -> 360
            else -> 0
        }
    }
}
