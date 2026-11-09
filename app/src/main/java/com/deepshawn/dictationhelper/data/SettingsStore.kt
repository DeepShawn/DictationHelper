package com.deepshawn.dictationhelper.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DarkMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

/** 主题配色。seed == null 且 monet == false 时使用 Miuix 默认配色 */
enum class ThemeAccent(val label: String, val seed: Long?, val monet: Boolean = false) {
    MIUIX("经典蓝", null),
    WALLPAPER("壁纸取色", null, monet = true),
    SAKURA("樱花粉", 0xFFE86A92),
    MINT("薄荷绿", 0xFF2BB673),
    AMBER("琥珀橙", 0xFFF29A2E),
    GRAPE("葡萄紫", 0xFF7B61FF),
    OCEAN("深海青", 0xFF0E9AA7),
    INK("水墨灰", 0xFF5F6B7A),
}

/** 聆听语言 */
enum class ListenLang(val label: String, val tag: String) {
    ZH("中文（可夹杂英文）", "zh-CN"),
    EN("English", "en-US"),
}

data class AppSettings(
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val accent: ThemeAccent = ThemeAccent.MIUIX,
    val glass: Boolean = true,
    val apiKey: String = "",
    val listenLang: ListenLang = ListenLang.ZH,
    val voiceLang: ListenLang = ListenLang.ZH,
    val hapticOnHit: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load() = AppSettings(
        darkMode = enumOr(prefs.getString("darkMode", null), DarkMode.SYSTEM),
        accent = enumOr(prefs.getString("accent", null), ThemeAccent.MIUIX),
        glass = prefs.getBoolean("glass", true),
        apiKey = prefs.getString("apiKey", "").orEmpty(),
        listenLang = enumOr(prefs.getString("listenLang", null), ListenLang.ZH),
        voiceLang = enumOr(prefs.getString("voiceLang", null), ListenLang.ZH),
        hapticOnHit = prefs.getBoolean("hapticOnHit", true),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(_state.value)
        _state.value = s
        prefs.edit {
            putString("darkMode", s.darkMode.name)
            putString("accent", s.accent.name)
            putBoolean("glass", s.glass)
            putString("apiKey", s.apiKey)
            putString("listenLang", s.listenLang.name)
            putString("voiceLang", s.voiceLang.name)
            putBoolean("hapticOnHit", s.hapticOnHit)
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        runCatching { enumValueOf<T>(name!!) }.getOrDefault(default)
}
