package com.kakaanime.provider

sealed class ProviderResult<out T> {
    data class Success<T>(val data: T) : ProviderResult<T>()
    data class Error(val providerId: String, val message: String, val throwable: Throwable? = null) : ProviderResult<Nothing>()
    data object Empty : ProviderResult<Nothing>()
}

data class ProviderHealth(
    val providerId: String,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val averageLatencyMs: Long = 0L,
    val lastCheckedAt: Long = 0L,
) {
    val successRate: Double
        get() {
            val total = successfulRequests + failedRequests
            return if (total == 0) 0.0 else successfulRequests.toDouble() / total
        }
}
