package com.kakaanime.provider

class SmartProviderRouter(
    private val registry: ProviderRegistry,
    private val failureThreshold: Int = 2,
    private val cooldownMs: Long = 30_000L
) {
    private enum class Operation { SEARCH, ANIME, EPISODES, STREAMS }
    private data class HealthState(var failures: Int = 0, var unavailableUntil: Long = 0L)
    private val health = mutableMapOf<Pair<String, Operation>, HealthState>()

    suspend fun search(query: String): List<ProviderAnime> {
        if (query.isBlank()) return emptyList()
        return eligibleProviders(Operation.SEARCH).flatMap { provider ->
            runCatching { provider.search(query.trim()) }
                .onSuccess { markSuccess(provider.id, Operation.SEARCH) }
                .onFailure { markFailure(provider.id, Operation.SEARCH) }
                .getOrDefault(emptyList())
                .map { it.copy(providerId = provider.id) }
        }.distinctBy { buildKey(it.title, it.year, it.seasonNumber, it.seasonTitle, it.animeGroupId) }
    }

    suspend fun getAnime(animeId: String): ProviderAnime? =
        if (animeId.isBlank()) null else forEachProvider(Operation.ANIME) { it.getAnime(animeId)?.copy(providerId = it.id) }

    suspend fun getEpisodes(animeId: String): List<ProviderEpisode> =
        if (animeId.isBlank()) emptyList() else forEachProvider(Operation.EPISODES) { provider ->
            provider.getEpisodes(animeId).takeIf { it.isNotEmpty() }
                ?.map { it.copy(providerId = provider.id) }?.sortedBy { it.number }
        } ?: emptyList()

    suspend fun getStreams(animeId: String, episodeNumber: Int): List<ProviderStream> {
        if (animeId.isBlank() || episodeNumber < 1) return emptyList()
        return eligibleProviders(Operation.STREAMS).flatMap { provider ->
            runCatching { provider.getStreams(animeId, episodeNumber) }
                .onSuccess { markSuccess(provider.id, Operation.STREAMS) }
                .onFailure { markFailure(provider.id, Operation.STREAMS) }
                .getOrDefault(emptyList()).map { it.copy(providerId = provider.id) }
        }.filter { it.url.isNotBlank() }
            .distinctBy { Triple(it.providerId, it.url, it.quality) }
            .sortedWith(compareByDescending<ProviderStream> { qualityScore(it.quality) }
                .thenBy { providerPriority(it.providerId) }.thenBy { it.providerId })
    }

    private suspend fun <T> forEachProvider(operation: Operation, action: suspend (AnimeProvider) -> T?): T? {
        for (provider in eligibleProviders(operation)) {
            val result = runCatching { action(provider) }
                .onSuccess { if (it != null) markSuccess(provider.id, operation) }
                .onFailure { markFailure(provider.id, operation) }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun eligibleProviders(operation: Operation) = registry.all().filter { isEligible(it.id, operation) }
    private fun isEligible(id: String, op: Operation): Boolean {
        val key = id to op; val state = health[key] ?: return true; val now = System.currentTimeMillis()
        if (state.unavailableUntil <= now) { health.remove(key); return true }
        return false
    }
    private fun markSuccess(id: String, op: Operation) { health.remove(id to op) }
    private fun markFailure(id: String, op: Operation) {
        val state = health.getOrPut(id to op) { HealthState() }; state.failures++
        if (state.failures >= failureThreshold) state.unavailableUntil = System.currentTimeMillis() + cooldownMs
    }
    private fun providerPriority(id: String) = registry.get(id)?.priority ?: Int.MAX_VALUE
    private fun buildKey(title: String, year: Int?, season: Int?, seasonTitle: String?, group: String) =
        "${group.trim().lowercase().ifBlank { title.trim().lowercase() }}|${title.trim().lowercase().replace(Regex("\\s+"), " ")}|${year ?: 0}|${season ?: "na"}|${seasonTitle?.trim()?.lowercase()?.replace(Regex("\\s+"), " ").orEmpty()}"
    private fun qualityScore(q: String?): Int = when {
        q?.contains("1080", true) == true -> 1080
        q?.contains("720", true) == true -> 720
        q?.contains("480", true) == true -> 480
        q?.contains("360", true) == true -> 360
        else -> 0
    }
}
