package com.deepshawn.dictationhelper

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshawn.dictationhelper.ui.MainScreen
import com.deepshawn.dictationhelper.ui.glass.LocalGlassEnabled
import com.deepshawn.dictationhelper.ui.glass.LocalIsDark
import com.deepshawn.dictationhelper.ui.theme.AppTheme
import com.deepshawn.dictationhelper.ui.theme.isAppDark

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = isAppDark(settings)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
                onDispose {}
            }
            CompositionLocalProvider(
                LocalIsDark provides dark,
                LocalGlassEnabled provides settings.glass,
            ) {
                AppTheme(settings) {
                    MainScreen(vm)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 退到后台时停止聆听，避免持续占用麦克风
        if (!isChangingConfigurations) vm.stopSpeech()
    }
}
