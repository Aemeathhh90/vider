package com.kakaanime.provider

/** Factory is intentionally small until concrete adapters are migrated. */
object ProviderFactory {
    fun createRegistry(providers: List<AnimeProvider> = emptyList()): ProviderRegistry =
        ProviderRegistry().apply { registerAll(providers) }

    fun createEngine(providers: List<AnimeProvider> = emptyList()): ProviderEngine =
        ProviderEngine(createRegistry(providers))
}
