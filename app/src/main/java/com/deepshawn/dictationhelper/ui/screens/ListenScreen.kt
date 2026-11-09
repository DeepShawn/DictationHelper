package com.deepshawn.dictationhelper.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshawn.dictationhelper.MainViewModel
import com.deepshawn.dictationhelper.SpeechMode
import com.deepshawn.dictationhelper.data.ListenLang
import com.deepshawn.dictationhelper.ui.components.GlassButton
import com.deepshawn.dictationhelper.ui.components.WordCard
import com.deepshawn.dictationhelper.ui.glass.AppBackground
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 环境聆听：持续识别周围的语音（老师报听写、广播、视频…），
 * 自动在词表中找出对应的词汇 / 短语 / 句子，支持模糊音与中文释义反查。
 */
@Composable
fun ListenScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val mode by vm.speechMode.collectAsStateWithLifecycle()
    val partial by vm.speech.partial.collectAsStateWithLifecycle()
    val level by vm.speech.level.collectAsStateWithLifecycle()
    val error by vm.speech.error.collectAsStateWithLifecycle()
    val transcript by vm.transcript.collectAsStateWithLifecycle()
    val hits by vm.hits.collectAsStateWithLifecycle()
    val listening = mode == SpeechMode.LISTEN

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        vm.hitEvents.collect { if (vm.settings.value.hapticOnHit) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    // 聆听时保持屏幕常亮
    val view = LocalView.current
    DisposableEffect(listening) {
        view.keepScreenOn = listening
        onDispose { view.keepScreenOn = false }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening() else vm.toast("需要麦克风权限才能聆听")
    }
    fun toggle() {
        if (listening) {
            vm.stopSpeech()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startListening()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val backdrop = rememberLayerBackdrop()
    val scrollBehavior = MiuixScrollBehavior()
    val primary = MiuixTheme.colorScheme.primary

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            AppBackground()
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = "环境聆听",
                        subtitle = if (listening) "正在聆听 · ${settings.listenLang.label}" else "听到词表里的内容会自动标出",
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(onClick = vm::clearHits) { Icon(MiuixIcons.Delete, contentDescription = "清空记录") }
                        },
                    )
                },
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = BottomBarSpace + 150.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        TabRowWithContour(
                            tabs = ListenLang.entries.map { it.label },
                            selectedTabIndex = settings.listenLang.ordinal,
                            onTabSelected = { vm.setListenLang(ListenLang.entries[it]) },
                        )
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("实时识别", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                Spacer(Modifier.height(6.dp))
                                val lines = transcript.takeLast(3)
                                if (lines.isEmpty() && partial.isBlank()) {
                                    Text(
                                        error ?: if (listening) "请说话…" else "点击下方麦克风开始聆听",
                                        fontSize = 15.sp,
                                        color = if (error != null) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                lines.forEach {
                                    Text(it, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                                if (partial.isNotBlank()) {
                                    Text(partial, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = primary)
                                }
                            }
                        }
                    }
                    item { SmallTitle(text = "命中的词条（${hits.size}）", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) }
                    if (hits.isEmpty()) {
                        item {
                            Text(
                                "还没有听到词表里的内容",
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            )
                        }
                    }
                    items(hits, key = { it.entry.id }) { hit ->
                        WordCard(
                            entry = hit.entry,
                            highlight = hit.entry.id == hits.firstOrNull()?.entry?.id,
                            extraTag = if (hit.viaTranslation) "按释义命中" else "匹配度 ${(hit.score * 100).toInt()}%",
                            modifier = Modifier.animateItem(),
                            trailing = {
                                Text("×${hit.count}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primary)
                            },
                        )
                    }
                }
            }
        }

        // 大号玻璃麦克风按钮 + 音量光圈
        val pulse = rememberInfiniteTransition(label = "pulse")
        val breathe by pulse.animateFloat(1f, 1.08f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "breathe")
        val ring by animateFloatAsState(if (listening) 1f + level * 0.45f else 1f, label = "ring")
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = BottomBarSpace - 12.dp)
                .size(150.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (listening) {
                Box(
                    Modifier
                        .size(104.dp)
                        .scale(ring * breathe)
                        .background(primary.copy(alpha = 0.22f), CircleShape),
                )
            }
            GlassButton(
                backdrop = backdrop,
                onClick = ::toggle,
                size = 96.dp,
                tint = if (listening) primary else Color.Unspecified,
            ) {
                Icon(
                    if (listening) MiuixIcons.Pause else MiuixIcons.Mic,
                    contentDescription = if (listening) "停止聆听" else "开始聆听",
                    tint = if (listening) Color.White else primary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}
