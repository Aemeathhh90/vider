package com.kakaanime.provider.extractor

import com.kakaanime.provider.ProviderStream

interface StreamExtractor {
    val id: String
    val priority: Int
    fun canHandle(url: String): Boolean
    suspend fun extract(url: String, referer: String? = null): List<ProviderStream>
}
