package com.kakaanime.provider.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kakaanime.provider.ProviderStream
import com.kakaanime.provider.StreamType
import com.kakaanime.provider.extractor.BrowserStreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Android-only browser adapter. The provider resolver talks to this through
 * BrowserStreamResolver and therefore does not need to know about WebView.
 */
class WebViewBrowserStreamResolver(
    private val context: Context,
    private val timeoutMs: Long = 20_000L
) : BrowserStreamResolver {
    override suspend fun resolve(url: String, referer: String?): List<ProviderStream> =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                val handler = Handler(Looper.getMainLooper())
                var webView: WebView? = null

                fun finish(result: List<ProviderStream>) {
                    if (!finished.compareAndSet(false, true)) return
                    handler.post {
                        handler.removeCallbacksAndMessages(null)
                        webView?.stopLoading()
                        webView?.destroy()
                        webView = null
                        if (continuation.isActive) continuation.resume(result)
                    }
                }

                fun capture(candidate: String, requestHeaders: Map<String, String> = emptyMap()) {
                    val type = candidate.toStreamType() ?: return
                    val cookie = runCatching { CookieManager.getInstance().getCookie(candidate) }.getOrNull()
                    val headers = buildMap {
                        put("User-Agent", USER_AGENT)
                        referer?.takeIf(String::isNotBlank)?.let { put("Referer", it) }
                        requestHeaders["Referer"]?.takeIf(String::isNotBlank)?.let { put("Referer", it) }
                        cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
                    }
                    finish(listOf(ProviderStream(
                        providerId = "webview-resolver",
                        url = candidate,
                        type = type,
                        headers = headers
                    )))
                }

                handler.postDelayed({ finish(emptyList()) }, timeoutMs)

                webView = WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.userAgentString = USER_AGENT
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            if (!request.isForMainFrame) capture(request.url.toString(), request.requestHeaders)
                            return null
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
                            capture(url)
                            return null
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    handler.post {
                        if (finished.compareAndSet(false, true)) {
                            handler.removeCallbacksAndMessages(null)
                            webView?.stopLoading()
                            webView?.destroy()
                            webView = null
                        }
                    }
                }

                runCatching {
                    val headers = buildMap {
                        put("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
                        referer?.takeIf(String::isNotBlank)?.let { put("Referer", it) }
                    }
                    webView?.loadUrl(url, headers)
                }.onFailure { finish(emptyList()) }
            }
        }

    private fun String.toStreamType(): StreamType? = when {
        contains(".m3u8", true) -> StreamType.HLS
        contains(".mpd", true) -> StreamType.DASH
        contains(".mp4", true) || contains(".webm", true) -> StreamType.MP4
        else -> null
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
