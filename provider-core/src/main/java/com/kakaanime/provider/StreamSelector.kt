package com.kakaanime.provider

object StreamSelector {
    fun best(streams: List<NormalizedStream>, preferredQuality: StreamQuality? = null, premium: Boolean = false): NormalizedStream? {
        val available = streams.filter { it.type != StreamType.UNKNOWN && (!it.isPremium || premium) }
        if (available.isEmpty()) return null
        if (preferredQuality != null) available.firstOrNull { it.quality == preferredQuality }?.let { return it }
        return available.maxByOrNull { it.quality.value }
    }

    fun allAvailable(streams: List<NormalizedStream>, premium: Boolean = false): List<NormalizedStream> =
        streams.filter { it.type != StreamType.UNKNOWN && (premium || !it.isPremium) }
            .sortedByDescending { it.quality.value }
}
