package com.kakaanime.provider

sealed class ProviderResult<out T> {
    data class Success<T>(val data: T) : ProviderResult<T>()
    data class Error(
        val providerId: String,
        val message: String,
        val throwable: Throwable? = null
    ) : ProviderResult<Nothing>()
    data object Empty : ProviderResult<Nothing>()
}
