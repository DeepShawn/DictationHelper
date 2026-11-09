package com.deepshawn.dictationhelper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshawn.dictationhelper.MainViewModel
import com.deepshawn.dictationhelper.data.EntryType
import com.deepshawn.dictationhelper.data.WordEntry
import com.deepshawn.dictationhelper.ui.components.ConfirmDialog
import com.deepshawn.dictationhelper.ui.components.GlassButton
import com.deepshawn.dictationhelper.ui.components.ImportActions
import com.deepshawn.dictationhelper.ui.components.ImportSheet
import com.deepshawn.dictationhelper.ui.components.WordCard
import com.deepshawn.dictationhelper.ui.components.WordEditorSheet
import com.deepshawn.dictationhelper.ui.glass.AppBackground
import com.deepshawn.dictationhelper.util.TextUtils
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 底部悬浮导航栏占用的高度，列表需要留出空间 */
val BottomBarSpace = 100.dp

@Composable
fun WordsScreen(vm: MainViewModel, actions: ImportActions) {
    val words by vm.words.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val aiBusy by vm.aiBusy.collectAsStateWithLifecycle()

    var filterType by rememberSaveable { mutableIntStateOf(0) }
    var filterText by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<WordEntry?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<WordEntry?>(null) }

    val shown by remember(words) {
        derivedStateOf {
            val q = TextUtils.normalize(filterText)
            words.filter { w ->
                (filterType == 0 || w.type == EntryType.entries[filterType - 1]) &&
                    (q.isEmpty() || TextUtils.normalize(w.text).contains(q) || TextUtils.normalize(w.translation).contains(q))
            }
        }
    }

    val backdrop = rememberLayerBackdrop()
    val scrollBehavior = MiuixScrollBehavior()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            AppBackground()
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = "词表",
                        subtitle = "共 ${words.size} 条",
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(onClick = { showImport = true }) {
                                Icon(MiuixIcons.Import, contentDescription = "导入")
                            }
                        },
                    )
                },
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = BottomBarSpace + 80.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextField(
                                value = filterText,
                                onValueChange = { filterText = it },
                                label = "筛选原文或释义",
                                singleLine = true,
                                leadingIcon = {
                                    Icon(MiuixIcons.Search, contentDescription = null, modifier = Modifier.padding(start = 12.dp).size(20.dp))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TabRow(
                                tabs = listOf("全部") + EntryType.entries.map { it.label },
                                selectedTabIndex = filterType,
                                onTabSelected = { filterType = it },
                            )
                        }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                if (words.isEmpty()) "词表为空\n点击右下角 + 添加，或右上角导入（拍照识别 / JSON）" else "没有符合条件的词条",
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            )
                        }
                    }
                    items(shown, key = { it.id }) { w ->
                        WordCard(
                            entry = w,
                            modifier = Modifier.animateItem(),
                            onClick = { editing = w; showEditor = true },
                            onLongPress = { deleting = w },
                        )
                    }
                }
            }
        }

        GlassButton(
            backdrop = backdrop,
            onClick = { editing = null; showEditor = true },
            tint = MiuixTheme.colorScheme.primary,
            size = 60.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = BottomBarSpace),
        ) {
            Icon(MiuixIcons.Add, contentDescription = "添加", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }

    WordEditorSheet(
        show = showEditor,
        entry = editing,
        aiBusy = aiBusy,
        hasApiKey = settings.apiKey.isNotBlank(),
        onDismiss = { showEditor = false },
        onAiComplete = { text, apply ->
            vm.complete(text) { c ->
                apply(c.translation, c.note, c.type?.let { t -> EntryType.entries.firstOrNull { it.name.equals(t, true) } })
            }
        },
        onSave = { text, tr, note, type ->
            val base = editing
            val entry = if (base == null) {
                vm.newEntry(com.deepshawn.dictationhelper.data.Draft(text, tr, note, type))
            } else {
                base.copy(text = text, translation = tr, note = note, type = type, lang = TextUtils.detectLang(text))
            }
            vm.save(entry)
            showEditor = false
        },
        onDelete = editing?.let { e -> { deleting = e; showEditor = false } },
    )

    ImportSheet(
        show = showImport,
        hasApiKey = settings.apiKey.isNotBlank(),
        onDismiss = { showImport = false },
        onTakePhoto = actions.takePhoto,
        onPickImage = actions.pickImage,
        onPickJson = actions.pickJson,
        onPasteJson = actions.pasteJson,
        onExport = actions.exportJson,
    )

    ConfirmDialog(
        show = deleting != null,
        title = "删除词条",
        summary = "确定删除「${deleting?.text.orEmpty()}」吗？",
        confirmText = "删除",
        onDismiss = { deleting = null },
        onConfirm = { deleting?.let(vm::delete) },
    )
}
