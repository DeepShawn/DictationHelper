package com.deepshawn.dictationhelper.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshawn.dictationhelper.MainViewModel
import com.deepshawn.dictationhelper.ui.components.BusyDialog
import com.deepshawn.dictationhelper.ui.components.GlassNavBar
import com.deepshawn.dictationhelper.ui.components.NavItem
import com.deepshawn.dictationhelper.ui.components.VisionReviewSheet
import com.deepshawn.dictationhelper.ui.components.rememberImportActions
import com.deepshawn.dictationhelper.ui.screens.ListenScreen
import com.deepshawn.dictationhelper.ui.screens.SearchScreen
import com.deepshawn.dictationhelper.ui.screens.SettingsScreen
import com.deepshawn.dictationhelper.ui.screens.WordsScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Recording
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings

private val navItems = listOf(
    NavItem("词表", MiuixIcons.Notes),
    NavItem("聆听", MiuixIcons.Recording),
    NavItem("检索", MiuixIcons.Search),
    NavItem("设置", MiuixIcons.Settings),
)

@Composable
fun MainScreen(vm: MainViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = tab != 0) { tab = 0 }

    val actions = rememberImportActions(vm)
    val visionDrafts by vm.visionDrafts.collectAsStateWithLifecycle()
    val visionBusy by vm.visionBusy.collectAsStateWithLifecycle()

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> WordsScreen(vm, actions)
                    1 -> ListenScreen(vm)
                    2 -> SearchScreen(vm)
                    else -> SettingsScreen(vm, actions)
                }
            }
        }
        GlassNavBar(
            backdrop = backdrop,
            items = navItems,
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    VisionReviewSheet(
        drafts = visionDrafts,
        onDismiss = vm::dismissVision,
        onImport = { vm.importDrafts(it); vm.dismissVision() },
    )
    BusyDialog(show = visionBusy, title = "DeepSeek 正在识别图片…")
}
