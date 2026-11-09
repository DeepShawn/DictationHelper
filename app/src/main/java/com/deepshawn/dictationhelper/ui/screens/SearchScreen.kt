package com.deepshawn.dictationhelper.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshawn.dictationhelper.MainViewModel
import com.deepshawn.dictationhelper.SpeechMode
import com.deepshawn.dictationhelper.data.ListenLang
import com.deepshawn.dictationhelper.ui.components.GlassButton
import com.deepshawn.dictationhelper.ui.components.GlassPanel
import com.deepshawn.dictationhelper.ui.components.VirtualKeyboard
import com.deepshawn.dictationhelper.ui.components.WordCard
import com.deepshawn.dictationhelper.ui.glass.AppBackground
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 检索：内置虚拟键盘输入首字母即可查看对应词条；也可语音输入（模糊识别）。
 */
@Composable
fun SearchScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val letters by vm.letters.collectAsStateWithLifecycle()
    val voiceQuery by vm.voiceQuery.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val mode by vm.speechMode.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val voiceActive = mode == SpeechMode.VOICE_SEARCH

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startVoiceSearch() else vm.toast("需要麦克风权限才能语音输入")
    }
    fun voice() {
        when {
            voiceActive -> vm.stopSpeech()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> vm.startVoiceSearch()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    var keyboardHeight by remember { mutableStateOf(260.dp) }
    val colors = MiuixTheme.colorScheme

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            AppBackground()
            LazyColumn(
                modifier = Modifier.fillMaxSize().overScrollVertical(),
                contentPadding = PaddingValues(
                    top = 96.dp + with(density) { WindowInsets.statusBars.getTop(density).toDp() },
                    bottom = keyboardHeight + BottomBarSpace + 24.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (results.isEmpty()) {
                    item {
                        Text(
                            when {
                                voiceQuery != null -> "没有找到与「${voiceQuery!!.substringBefore('\n')}」相近的词条"
                                letters.isNotEmpty() -> "没有以「${letters.uppercase()}」开头的词条"
                                else -> "词表为空"
                            },
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariantSummary,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        )
                    }
                }
                items(results, key = { it.entry.id }) { m ->
                    WordCard(
                        entry = m.entry,
                        modifier = Modifier.animateItem(),
                        extraTag = when {
                            voiceQuery == null -> null
                            m.viaTranslation -> "释义相近 ${(m.score * 100).toInt()}%"
                            else -> "相似度 ${(m.score * 100).toInt()}%"
                        },
                    )
                }
            }
        }

        // 顶部：查询内容 + 语音按钮（玻璃）
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassPanel(backdrop, Modifier.weight(1f).height(64.dp), corner = 32.dp) {
                Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
                    val (title, sub) = when {
                        voiceActive -> (voiceQuery?.substringBefore('\n') ?: "请说话…") to "正在聆听（${settings.voiceLang.label}）"
                        voiceQuery != null -> voiceQuery!!.substringBefore('\n') to "语音模糊检索 · 共 ${results.size} 条"
                        letters.isNotEmpty() -> letters.uppercase() to "首字母检索 · 共 ${results.size} 条"
                        else -> "输入首字母检索" to "中文按拼音首字母，英文按前缀"
                    }
                    Text(
                        title,
                        fontSize = if (letters.isNotEmpty() && voiceQuery == null) 22.sp else 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = if (letters.isNotEmpty() && voiceQuery == null) 3.sp else 0.sp,
                        color = if (letters.isEmpty() && voiceQuery == null && !voiceActive) colors.onSurfaceVariantSummary else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(sub, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1)
                }
            }
            GlassButton(
                backdrop = backdrop,
                onClick = {
                    val next = if (settings.voiceLang == ListenLang.ZH) ListenLang.EN else ListenLang.ZH
                    vm.updateSettings { it.copy(voiceLang = next) }
                    vm.toast("语音输入：${next.label}")
                },
                size = 48.dp,
            ) {
                Icon(MiuixIcons.Translate, contentDescription = "切换语音语言", tint = colors.onSurface, modifier = Modifier.size(22.dp))
            }
            GlassButton(
                backdrop = backdrop,
                onClick = ::voice,
                size = 64.dp,
                tint = if (voiceActive) colors.primary else Color.Unspecified,
            ) {
                Icon(
                    if (voiceActive) MiuixIcons.Pause else MiuixIcons.Mic,
                    contentDescription = "语音输入",
                    tint = if (voiceActive) Color.White else colors.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // 底部：虚拟键盘
        VirtualKeyboard(
            backdrop = backdrop,
            onKey = vm::typeLetter,
            onBackspace = vm::backspace,
            onClear = vm::clearQuery,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = BottomBarSpace - 8.dp)
                .onSizeChanged { keyboardHeight = with(density) { it.height.toDp() } },
        )
    }
}
