package com.deepshawn.dictationhelper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.deepshawn.dictationhelper.data.AppSettings
import com.deepshawn.dictationhelper.data.DarkMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun isAppDark(settings: AppSettings): Boolean = when (settings.darkMode) {
    DarkMode.SYSTEM -> isSystemInDarkTheme()
    DarkMode.LIGHT -> false
    DarkMode.DARK -> true
}

@Composable
fun AppTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val accent = settings.accent
    val controller = remember(settings.darkMode, accent) {
        val useMonet = accent.monet || accent.seed != null
        val mode = when (settings.darkMode) {
            DarkMode.SYSTEM -> if (useMonet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
            DarkMode.LIGHT -> if (useMonet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
            DarkMode.DARK -> if (useMonet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
        }
        ThemeController(
            colorSchemeMode = mode,
            keyColor = accent.seed?.let { Color(it) },
            paletteStyle = ThemePaletteStyle.TonalSpot,
        )
    }
    MiuixTheme(controller = controller, content = content)
}

/** 当前主题色，便于在非 Miuix 组件中引用 */
val primaryColor: Color
    @Composable get() = MiuixTheme.colorScheme.primary
