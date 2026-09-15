package com.kakaanime.provider

object ProviderStreamDeduplicator {
    fun deduplicate(
        streams: List<ProviderStream>,
        providerPriorities: Map<String, Int> = emptyMap()
    ): List<ProviderStream> = streams
        .filter { it.url.isNotBlank() }
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
        val uri = java.net.URI(raw.trim())
        java.net.URI(uri.scheme?.lowercase(), uri.userInfo, uri.host?.lowercase(), uri.port, uri.path?.trimEnd('/'), uri.query, null).toString()
    }.getOrElse { raw.trim().trimEnd('/') }

    private fun qualityRank(quality: String?): Int = when {
        quality?.contains("2160", true) == true || quality?.contains("4k", true) == true -> 2160
        quality?.contains("1440", true) == true || quality?.contains("2k", true) == true -> 1440
        quality?.contains("1080", true) == true || quality?.contains("fhd", true) == true -> 1080
        quality?.contains("720", true) == true || quality?.contains("hd", true) == true -> 720
        quality?.contains("480", true) == true -> 480
        quality?.contains("360", true) == true -> 360
        else -> 0
    }
}
