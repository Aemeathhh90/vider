package com.kakaanime.provider

data class ProviderHealth(
    val providerId: String,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val averageLatencyMs: Long = 0L,
    val lastCheckedAt: Long = 0L
) {
    val successRate: Double
        get() {
            val total = successfulRequests + failedRequests
            if (total == 0) return 0.0
            return successfulRequests.toDouble() / total.toDouble()
        }
}
