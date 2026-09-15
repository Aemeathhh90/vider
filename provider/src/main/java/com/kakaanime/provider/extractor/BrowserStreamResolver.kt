package com.kakaanime.provider.extractor

import com.kakaanime.provider.ProviderStream

/** Optional Android/browser fallback boundary. Provider core does not depend on WebView. */
fun interface BrowserStreamResolver {
    suspend fun resolve(url: String, referer: String?): List<ProviderStream>
}
