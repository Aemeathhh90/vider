package com.kakaanime.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SmartProviderRouter(
    private val registry: ProviderRegistry,
    private val failureThreshold: Int = 2,
    private val cooldownMs: Long = 30_000L
) {
    private enum class Operation { SEARCH, ANIME, EPISODES, STREAMS }
    private data class HealthState(var failures: Int = 0, var unavailableUntil: Long = 0L)
    private data class RaceResult<T>(val provider: AnimeProvider, val value: T?)
    private val health = ConcurrentHashMap<Pair<String, Operation>, HealthState>()

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
        if (animeId.isBlank()) null else raceProviders(Operation.ANIME) { provider ->
            provider.getAnime(animeId)?.copy(providerId = provider.id)
        }

    suspend fun getEpisodes(animeId: String): List<ProviderEpisode> =
        if (animeId.isBlank()) emptyList() else raceProviders(Operation.EPISODES) { provider ->
            provider.getEpisodes(animeId).takeIf { it.isNotEmpty() }
                ?.map { it.copy(providerId = provider.id) }
                ?.sortedBy { it.number }
        }.orEmpty()

    suspend fun getStreams(animeId: String, episodeNumber: Int): List<ProviderStream> {
        if (animeId.isBlank() || episodeNumber < 1) return emptyList()
        return raceProviders(Operation.STREAMS) { provider ->
            provider.getStreams(animeId, episodeNumber)
                .filter { it.url.isNotBlank() }
                .takeIf { it.isNotEmpty() }
                ?.map { it.copy(providerId = provider.id) }
        }.orEmpty()
            .distinctBy { Triple(it.providerId, it.url, it.quality) }
    }

    /** First-success race: all eligible providers start together. */
    private suspend fun <T> raceProviders(
        operation: Operation,
        action: suspend (AnimeProvider) -> T?
    ): T? = coroutineScope {
        val providers = eligibleProviders(operation)
        if (providers.isEmpty()) return@coroutineScope null

        val results = Channel<RaceResult<T>>(Channel.UNLIMITED)
        val jobs = providers.map { provider ->
            launch {
                val result = try {
                    action(provider)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    markFailure(provider.id, operation)
                    null
                }

                if (result != null) {
                    markSuccess(provider.id, operation)
                    results.send(RaceResult(provider, result))
                } else {
                    results.send(RaceResult(provider, null))
                }
            }
        }

        var remaining = providers.size
        var winner: T? = null
        while (remaining > 0 && winner == null) {
            val result = results.receive()
            remaining--
            if (result.value != null) {
                winner = result.value
                jobs.forEach(Job::cancel)
            }
        }

        results.close()
        winner
    }

    private fun eligibleProviders(operation: Operation) =
        registry.all().filter { isEligible(it.id, operation) }

    private fun isEligible(id: String, op: Operation): Boolean {
        val key = id to op
        val state = health[key] ?: return true
        val now = System.currentTimeMillis()
        if (state.unavailableUntil <= now) {
            health.remove(key, state)
            return true
        }
        return false
    }

    private fun markSuccess(id: String, op: Operation) {
        health.remove(id to op)
    }

    private fun markFailure(id: String, op: Operation) {
        val key = id to op
        synchronized(health) {
            val state = health[key] ?: HealthState().also { health[key] = it }
            state.failures++
            if (state.failures >= failureThreshold) {
                state.unavailableUntil = System.currentTimeMillis() + cooldownMs
            }
        }
    }

    private fun buildKey(title: String, year: Int?, season: Int?, seasonTitle: String?, group: String) =
        "${group.trim().lowercase().ifBlank { title.trim().lowercase() }}|${title.trim().lowercase().replace(Regex("\\s+"), " ")}|${year ?: 0}|${season ?: "na"}|${seasonTitle?.trim()?.lowercase()?.replace(Regex("\\s+"), " ").orEmpty()}"
}
