package com.example.channeltube

import android.net.http.HttpResponseCache
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.channeltube.ui.ChannelTubeApp
import com.example.channeltube.ui.ChannelTubeTheme
import com.example.channeltube.ui.WarmPlayerManager
import com.example.channeltube.ui.WebViewPrewarmer
import com.example.channeltube.viewmodel.MainViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Install HTTP response disk cache for YouTube Data API metadata caching
        try {
            if (HttpResponseCache.getInstalled() == null) {
                val httpCacheDir = File(cacheDir, "http_cache")
                HttpResponseCache.install(httpCacheDir, 25L * 1024 * 1024)
            }
        } catch (_: Exception) {}

        // 2. High performance Coil image loader with aggressive memory + disk caching
        try {
            val imageLoader = ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(cacheDir, "image_cache"))
                        .maxSizeBytes(100L * 1024 * 1024)
                        .build()
                }
                .crossfade(150)
                .respectCacheHeaders(false)
                .build()
            Coil.setImageLoader(imageLoader)
        } catch (_: Exception) {}

        // 3. Pre-warm Chromium engine & YouTube IFrame API scripts
        WebViewPrewarmer.warmup(this)

        // 4. Pre-initialize warm YouTube players for instant video playback (~150ms)
        WarmPlayerManager.init(this)

        setContent {
            ChannelTubeTheme {
                val mainViewModel: MainViewModel = viewModel()
                ChannelTubeApp(viewModel = mainViewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        WarmPlayerManager.destroy()
    }
}
