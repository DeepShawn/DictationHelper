package com.deepshawn.dictationhelper.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.dictationhelper.data.Draft
import com.deepshawn.dictationhelper.data.EntryType
import com.deepshawn.dictationhelper.data.WordEntry
import com.deepshawn.dictationhelper.util.TextUtils
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 新建 / 编辑词条。
 * @param entry 为 null 时新建
 */
@Composable
fun WordEditorSheet(
    show: Boolean,
    entry: WordEntry?,
    aiBusy: Boolean,
    hasApiKey: Boolean,
    onDismiss: () -> Unit,
    onAiComplete: (String, (translation: String, note: String, type: EntryType?) -> Unit) -> Unit,
    onSave: (text: String, translation: String, note: String, type: EntryType) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var text by remember(entry, show) { mutableStateOf(entry?.text.orEmpty()) }
    var translation by remember(entry, show) { mutableStateOf(entry?.translation.orEmpty()) }
    var note by remember(entry, show) { mutableStateOf(entry?.note.orEmpty()) }
    var type by remember(entry, show) { mutableStateOf(entry?.type ?: EntryType.WORD) }
    var typeTouched by remember(entry, show) { mutableStateOf(entry != null) }

    LaunchedEffect(text, typeTouched) {
        if (!typeTouched) type = TextUtils.detectType(text)
    }

    WindowBottomSheet(
        show = show,
        title = if (entry == null) "添加词条" else "编辑词条",
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TabRowWithContour(
                tabs = EntryType.entries.map { it.label },
                selectedTabIndex = type.ordinal,
                onTabSelected = { type = EntryType.entries[it]; typeTouched = true },
            )
            TextField(value = text, onValueChange = { text = it }, label = "中文 / 英文：词汇、短语或句子", modifier = Modifier.fillMaxWidth())
            TextField(value = translation, onValueChange = { translation = it }, label = "释义 / 翻译", modifier = Modifier.fillMaxWidth())
            TextField(value = note, onValueChange = { note = it }, label = "备注（音标、拼音、例句…）", modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    text = if (aiBusy) "AI 补全中…" else if (hasApiKey) "AI 自动补全" else "AI 补全（需密钥）",
                    onClick = {
                        onAiComplete(text) { tr, nt, tp ->
                            if (tr.isNotBlank()) translation = tr
                            if (nt.isNotBlank()) note = nt
                            if (tp != null && !typeTouched) type = tp
                        }
                    },
                    enabled = !aiBusy && text.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
                if (aiBusy) InfiniteProgressIndicator(color = MiuixTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onDelete != null) {
                    TextButton(text = "删除", onClick = onDelete, modifier = Modifier.weight(1f))
                }
                TextButton(
                    text = "保存",
                    onClick = { onSave(text.trim(), translation.trim(), note.trim(), type) },
                    enabled = text.isNotBlank(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 导入入口：拍照识别 / 相册识别 / JSON 文件 / 剪贴板 JSON / 导出 */
@Composable
fun ImportSheet(
    show: Boolean,
    hasApiKey: Boolean,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickImage: () -> Unit,
    onPickJson: () -> Unit,
    onPasteJson: () -> Unit,
    onExport: () -> Unit,
) {
    WindowBottomSheet(show = show, title = "导入词表", onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            if (!hasApiKey) {
                Text(
                    "拍照/图片识别使用 DeepSeek 视觉模型，请先在「设置」中填写 API 密钥。",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Card {
                ArrowPreference(
                    title = "拍照识别",
                    summary = "拍下课本或作业，AI 自动提取词汇、短语、句子",
                    startAction = { Icon(MiuixIcons.Scan, contentDescription = null, modifier = Modifier.padding(end = 12.dp)) },
                    onClick = { onDismiss(); onTakePhoto() },
                )
                ArrowPreference(
                    title = "从相册选择图片",
                    summary = "识别截图、照片中的词表",
                    startAction = { Icon(MiuixIcons.Image, contentDescription = null, modifier = Modifier.padding(end = 12.dp)) },
                    onClick = { onDismiss(); onPickImage() },
                )
            }
            Spacer(Modifier.height(12.dp))
            Card {
                ArrowPreference(
                    title = "导入 JSON 文件",
                    summary = "支持本应用导出格式、字符串数组、对象数组",
                    startAction = { Icon(MiuixIcons.File, contentDescription = null, modifier = Modifier.padding(end = 12.dp)) },
                    onClick = { onDismiss(); onPickJson() },
                )
                ArrowPreference(
                    title = "从剪贴板导入 JSON",
                    startAction = { Icon(MiuixIcons.Paste, contentDescription = null, modifier = Modifier.padding(end = 12.dp)) },
                    onClick = { onDismiss(); onPasteJson() },
                )
                ArrowPreference(
                    title = "导出为 JSON",
                    startAction = { Icon(MiuixIcons.Download, contentDescription = null, modifier = Modifier.padding(end = 12.dp)) },
                    onClick = { onDismiss(); onExport() },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 视觉识别结果确认：勾选需要导入的条目 */
@Composable
fun VisionReviewSheet(
    drafts: List<Draft>?,
    onDismiss: () -> Unit,
    onImport: (List<Draft>) -> Unit,
) {
    val show = drafts != null
    val checked = remember(drafts) { mutableStateListOf<Boolean>().apply { repeat(drafts?.size ?: 0) { add(true) } } }
    WindowBottomSheet(show = show, title = "识别结果", onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            val list = drafts.orEmpty()
            Text(
                "共识别到 ${list.size} 条，已选 ${checked.count { it }} 条",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(list) { i, d ->
                    Card(onClick = { if (i < checked.size) checked[i] = !checked[i] }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                state = if (checked.getOrElse(i) { false }) ToggleableState.On else ToggleableState.Off,
                                onClick = { if (i < checked.size) checked[i] = !checked[i] },
                            )
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(d.text, fontSize = 16.sp)
                                if (d.translation.isNotBlank()) {
                                    Text(d.translation, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                            }
                            Tag((d.type ?: TextUtils.detectType(d.text)).label)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    text = "导入所选",
                    onClick = { onImport(list.filterIndexed { i, _ -> checked.getOrElse(i) { false } }) },
                    enabled = checked.any { it },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 通用确认对话框 */
@Composable
fun ConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(show = show, title = title, summary = summary, onDismissRequest = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            TextButton(
                text = confirmText,
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 全屏加载遮罩内容 */
@Composable
fun BusyDialog(show: Boolean, title: String) {
    WindowDialog(show = show, title = title) {
        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            InfiniteProgressIndicator(color = MiuixTheme.colorScheme.primary, size = 36.dp)
        }
    }
}
