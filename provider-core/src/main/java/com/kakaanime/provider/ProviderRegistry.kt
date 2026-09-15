package com.kakaanime.provider

class ProviderRegistry {
    private val providers = mutableListOf<AnimeProvider>()

    fun register(provider: AnimeProvider) {
        if (providers.none { it.id == provider.id }) providers += provider
    }

    fun registerAll(items: List<AnimeProvider>) = items.forEach(::register)

    fun remove(providerId: String) {
        providers.removeAll { it.id == providerId }
    }

    fun get(providerId: String): AnimeProvider? = providers.firstOrNull { it.id == providerId }

    fun all(): List<AnimeProvider> = providers.sortedBy { it.priority }.toList()

    fun clear() = providers.clear()
}
