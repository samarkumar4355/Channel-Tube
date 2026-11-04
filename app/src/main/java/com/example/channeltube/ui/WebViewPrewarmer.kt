package com.example.channeltube.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pre-warms the Chromium native rendering engine, V8 engine, and preloads the YouTube
 * IFrame API scripts into the local HTTP disk cache at application launch.
 * This dramatically reduces the initial and subsequent video load latency.
 */
object WebViewPrewarmer {
    private var isWarmed = false

    @SuppressLint("SetJavaScriptEnabled")
    fun warmup(context: Context) {
        if (isWarmed) return
        isWarmed = true

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val dummy = WebView(context.applicationContext)
                dummy.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    val ua = userAgentString
                    if (ua.contains("; wv")) {
                        userAgentString = ua.replace("; wv", "")
                    }
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(dummy, true)

                // Pre-connect to YouTube edge domains and preload iframe_api script
                val preloadHtml = """
                    <!doctype html>
                    <html>
                    <head>
                        <link rel="preconnect" href="https://www.youtube.com">
                        <link rel="preconnect" href="https://www.googlevideo.com">
                        <link rel="preconnect" href="https://fonts.gstatic.com">
                        <script src="https://www.youtube.com/iframe_api"></script>
                    </head>
                    <body></body>
                    </html>
                """.trimIndent()

                dummy.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    preloadHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
            } catch (_: Throwable) {
                // Ignore any warmup failures gracefully
            }
        }
    }
}
