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
            return if (total == 0) 0.0 else successfulRequests.toDouble() / total
        }
}
