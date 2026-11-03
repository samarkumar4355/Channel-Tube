package com.example.channeltube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.channeltube.ui.ChannelTubeApp
import com.example.channeltube.ui.ChannelTubeTheme
import com.example.channeltube.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChannelTubeTheme {
                val mainViewModel: MainViewModel = viewModel()
                ChannelTubeApp(viewModel = mainViewModel)
            }
        }
    }
}
