package com.kakaanime.provider.extractor

import com.kakaanime.provider.ProviderStream
import com.kakaanime.provider.StreamType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class StreamResolver(
    private val registry: ExtractorRegistry,
    private val validator: StreamValidator = StreamValidator()
) {
    suspend fun resolve(urls: List<String>, referer: String? = null): List<ProviderStream> = supervisorScope {
        val inputs = urls.map(String::trim).filter(String::isNotBlank).distinct()
        if (inputs.isEmpty()) return@supervisorScope emptyList()

        val extracted = inputs.flatMap { url ->
            registry.find(url).map { extractor ->
                async { runCatching { extractor.extract(url, referer) }.getOrDefault(emptyList()) }
            }.awaitAll().flatten()
        }.filter { it.url.startsWith("http", true) }.distinctBy { it.url }

        val candidates = if (extracted.isNotEmpty()) extracted else inputs.map {
            ProviderStream(providerId = "resolver", url = it, type = StreamType.UNKNOWN, headers = referer?.let { ref -> mapOf("Referer" to ref) }.orEmpty())
        }

        candidates.map { async { validator.validate(it) } }
            .awaitAll()
            .filterNotNull()
            .filter { it.type != StreamType.UNKNOWN }
            .distinctBy { it.url }
    }
}
