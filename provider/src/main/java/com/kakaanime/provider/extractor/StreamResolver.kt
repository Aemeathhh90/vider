package com.kakaanime.provider.extractor

import com.kakaanime.provider.ProviderStream
import com.kakaanime.provider.StreamType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/**
 * Resolves provider/server URLs through the extractor chain.
 *
 * Only typed, validated streams are allowed to leave this resolver. A URL
 * being discovered is not enough to make it playable by Media3.
 *
 * Browser fallback is injected through [BrowserStreamResolver] so the provider
 * layer stays independent from Android WebView implementation details.
 */
class StreamResolver(
    private val registry: ExtractorRegistry,
    private val validator: StreamValidator = StreamValidator(),
    private val browserResolver: BrowserStreamResolver? = null
) {
    suspend fun resolve(
        urls: List<String>,
        referer: String? = null
    ): List<ProviderStream> = supervisorScope {
        val inputUrls = urls.map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        if (inputUrls.isEmpty()) return@supervisorScope emptyList()

        val extracted = inputUrls.flatMap { url ->
            registry.find(url).map { extractor ->
                async {
                    runCatching { extractor.extract(url, referer) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
            .filter { it.url.startsWith("http", ignoreCase = true) }
            .distinctBy { it.url }

        if (extracted.isEmpty()) {
            val direct = validateDirectUrls(inputUrls)
            if (direct.isNotEmpty()) return@supervisorScope direct
            return@supervisorScope resolveWithBrowser(inputUrls, referer)
        }

        val validated = validateCandidates(extracted)
        if (validated.isNotEmpty()) return@supervisorScope validated

        val direct = validateDirectUrls(inputUrls)
        if (direct.isNotEmpty()) return@supervisorScope direct

        val browserInputs = (extracted.map { it.url } + inputUrls).distinct()
        resolveWithBrowser(browserInputs, referer)
    }

    private suspend fun validateCandidates(
        candidates: List<ProviderStream>
    ): List<ProviderStream> = supervisorScope {
        candidates.map { candidate ->
            async { validator.validate(candidate) }
        }.awaitAll()
            .filterNotNull()
            .filter { it.type != StreamType.UNKNOWN }
            .distinctBy { it.url }
    }

    private suspend fun validateDirectUrls(
        urls: List<String>
    ): List<ProviderStream> = supervisorScope {
        urls.map { url ->
            async {
                validator.validate(
                    ProviderStream(
                        providerId = "",
                        url = url,
                        type = StreamType.UNKNOWN
                    )
                )
            }
        }.awaitAll()
            .filterNotNull()
            .filter { it.type != StreamType.UNKNOWN }
            .distinctBy { it.url }
    }

    private suspend fun resolveWithBrowser(
        urls: List<String>,
        referer: String?
    ): List<ProviderStream> {
        val resolver = browserResolver ?: return emptyList()
        return supervisorScope {
            urls.map { url ->
                async {
                    runCatching { resolver.resolve(url, referer) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll()
                .flatten()
                .filter { it.type != StreamType.UNKNOWN }
                .filter { it.url.startsWith("http", ignoreCase = true) }
                .distinctBy { it.url }
        }
    }
}
