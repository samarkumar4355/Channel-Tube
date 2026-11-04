package com.example.channeltube.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

interface PlayerEventCallback {
    fun onPlayerReady(player: YouTubePlayer)
    fun onCurrentSecond(second: Float)
    fun onVideoDuration(duration: Float)
    fun onStateChange(state: PlayerConstants.PlayerState)
    fun onError(description: String?)
    fun onEnterFullscreen(view: View, exitFullscreen: () -> Unit)
    fun onExitFullscreen()
}

/**
 * Singleton manager that retains pre-warmed YouTubePlayerView instances across the application.
 * By keeping the underlying Chromium WebViews alive in memory and pre-loading YouTube IFrame assets,
 * video playback starts in ~150-200ms instead of waiting seconds for cold WebView setup.
 */
object WarmPlayerManager {
    private var mainPlayerView: YouTubePlayerView? = null
    var mainPlayer: YouTubePlayer? = null
        private set
    var isMainReady: Boolean = false
        private set

    private var shortsPlayerView: YouTubePlayerView? = null
    var shortsPlayer: YouTubePlayer? = null
        private set
    var isShortsReady: Boolean = false
        private set

    var mainCallback: PlayerEventCallback? = null
    var shortsCallback: PlayerEventCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun init(activity: Activity) {
        if (mainPlayerView == null) {
            mainPlayerView = createAndWarmPlayer(activity, isForShorts = false)
        }
        if (shortsPlayerView == null) {
            shortsPlayerView = createAndWarmPlayer(activity, isForShorts = true)
        }
    }

    private fun createAndWarmPlayer(activity: Activity, isForShorts: Boolean): YouTubePlayerView {
        val playerView = YouTubePlayerView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            enableAutomaticInitialization = false
        }

        optimizeWebView(playerView)

        val options = IFramePlayerOptions.Builder(activity)
            .controls(if (isForShorts) 0 else 1)
            .autoplay(1)
            .mute(0)
            .fullscreen(1)
            .rel(0)
            .ivLoadPolicy(3)
            .ccLoadPolicy(0)
            .build()

        playerView.addFullscreenListener(object : FullscreenListener {
            override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                if (isForShorts) {
                    shortsCallback?.onEnterFullscreen(fullscreenView, exitFullscreen)
                } else {
                    mainCallback?.onEnterFullscreen(fullscreenView, exitFullscreen)
                }
            }

            override fun onExitFullscreen() {
                if (isForShorts) {
                    shortsCallback?.onExitFullscreen()
                } else {
                    mainCallback?.onExitFullscreen()
                }
            }
        })

        playerView.initialize(
            youTubePlayerListener = object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    if (isForShorts) {
                        shortsPlayer = youTubePlayer
                        isShortsReady = true
                    } else {
                        mainPlayer = youTubePlayer
                        isMainReady = true
                    }
                    youTubePlayer.unMute()
                    youTubePlayer.setVolume(100)
                    suppressYouTubeSpinner(playerView)

                    val cb = if (isForShorts) shortsCallback else mainCallback
                    cb?.onPlayerReady(youTubePlayer)
                }

                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                    suppressYouTubeSpinner(playerView)
                    val cb = if (isForShorts) shortsCallback else mainCallback
                    cb?.onCurrentSecond(second)
                }

                override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                    val cb = if (isForShorts) shortsCallback else mainCallback
                    cb?.onVideoDuration(duration)
                }

                override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                    if (state == PlayerConstants.PlayerState.PLAYING) {
                        youTubePlayer.unMute()
                        youTubePlayer.setVolume(100)
                    }
                    suppressYouTubeSpinner(playerView)
                    val cb = if (isForShorts) shortsCallback else mainCallback
                    cb?.onStateChange(state)
                }

                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                    val msg = when (error) {
                        PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ->
                            "This video has playback restrictions set by its owner."
                        PlayerConstants.PlayerError.VIDEO_NOT_FOUND ->
                            "This video could not be found or has been removed."
                        PlayerConstants.PlayerError.REQUEST_MISSING_HTTP_REFERER ->
                            "This video requires a valid referrer header."
                        else -> "Playback error: ${error.name}"
                    }
                    val cb = if (isForShorts) shortsCallback else mainCallback
                    cb?.onError(msg)
                }
            },
            handleNetworkEvents = true,
            playerOptions = options
        )

        return playerView
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun optimizeWebView(playerView: YouTubePlayerView) {
        val webView = findWebView(playerView) ?: return
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            val ua = userAgentString
            if (ua.contains("; wv")) {
                userAgentString = ua.replace("; wv", "")
            }
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    fun suppressYouTubeSpinner(playerView: YouTubePlayerView?) {
        val webView = playerView?.let { findWebView(it) } ?: return
        val js = """
            (function() {
                var css = '.ytp-spinner, .ytp-spinner-container, .ytp-spinner-rotator, .ytp-spinner-left, .ytp-spinner-right, .ytp-spinner-circle { display: none !important; opacity: 0 !important; visibility: hidden !important; animation: none !important; -webkit-animation: none !important; }';

                function applyStyle(doc) {
                    if (!doc) return;
                    try {
                        var styleId = 'channeltube-hide-spinner';
                        if (!doc.getElementById(styleId)) {
                            var s = doc.createElement('style');
                            s.id = styleId;
                            s.type = 'text/css';
                            s.appendChild(doc.createTextNode(css));
                            (doc.head || doc.documentElement || doc.body).appendChild(s);
                        }
                        var elements = doc.querySelectorAll('.ytp-spinner, .ytp-spinner-container, .ytp-spinner-rotator');
                        for (var i = 0; i < elements.length; i++) {
                            elements[i].style.setProperty('display', 'none', 'important');
                            elements[i].style.setProperty('opacity', '0', 'important');
                            elements[i].style.setProperty('visibility', 'hidden', 'important');
                        }
                    } catch(e) {}
                }

                applyStyle(document);

                if (!window._channeltubeSpinnerInterval) {
                    window._channeltubeSpinnerInterval = setInterval(function() {
                        applyStyle(document);
                        var iframes = document.getElementsByTagName('iframe');
                        for (var j = 0; j < iframes.length; j++) {
                            try {
                                var ifrDoc = iframes[j].contentDocument || iframes[j].contentWindow.document;
                                applyStyle(ifrDoc);
                            } catch(e) {}
                        }
                    }, 200);
                }
            })();
        """.trimIndent()
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    private fun findWebView(viewGroup: ViewGroup): WebView? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is WebView) return child
            if (child is ViewGroup) {
                val found = findWebView(child)
                if (found != null) return found
            }
        }
        return null
    }

    fun getMainPlayerView(context: Context): YouTubePlayerView {
        val view = mainPlayerView ?: createAndWarmPlayer(context as Activity, isForShorts = false).also {
            mainPlayerView = it
        }
        (view.parent as? ViewGroup)?.removeView(view)
        suppressYouTubeSpinner(view)
        return view
    }

    fun getShortsPlayerView(context: Context): YouTubePlayerView {
        val view = shortsPlayerView ?: createAndWarmPlayer(context as Activity, isForShorts = true).also {
            shortsPlayerView = it
        }
        (view.parent as? ViewGroup)?.removeView(view)
        suppressYouTubeSpinner(view)
        return view
    }

    fun detachMainPlayer() {
        mainPlayer?.pause()
        mainCallback = null
        (mainPlayerView?.parent as? ViewGroup)?.removeView(mainPlayerView)
    }

    fun detachShortsPlayer() {
        shortsPlayer?.pause()
        shortsCallback = null
        (shortsPlayerView?.parent as? ViewGroup)?.removeView(shortsPlayerView)
    }

    fun destroy() {
        try {
            mainPlayerView?.release()
            mainPlayerView = null
            mainPlayer = null
            isMainReady = false

            shortsPlayerView?.release()
            shortsPlayerView = null
            shortsPlayer = null
            isShortsReady = false
        } catch (_: Exception) {}
    }
}

