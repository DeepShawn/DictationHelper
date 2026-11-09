package com.deepshawn.dictationhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshawn.dictationhelper.BuildConfig
import com.deepshawn.dictationhelper.MainViewModel
import com.deepshawn.dictationhelper.ai.DeepSeekClient
import com.deepshawn.dictationhelper.data.DarkMode
import com.deepshawn.dictationhelper.data.ListenLang
import com.deepshawn.dictationhelper.data.ThemeAccent
import com.deepshawn.dictationhelper.ui.components.ConfirmDialog
import com.deepshawn.dictationhelper.ui.components.ImportActions
import com.deepshawn.dictationhelper.ui.glass.AppBackground
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, actions: ImportActions) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val aiBusy by vm.aiBusy.collectAsStateWithLifecycle()
    val words by vm.words.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val uri = LocalUriHandler.current

    var keyDraft by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { TopAppBar(title = "设置", color = Color.Transparent, scrollBehavior = scrollBehavior) },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = BottomBarSpace + 16.dp, start = 16.dp, end = 16.dp),
            ) {
                // ---------------- 外观 ----------------
                item { SmallTitle("外观", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        WindowDropdownPreference(
                            title = "深色模式",
                            items = DarkMode.entries.map { it.label },
                            selectedIndex = settings.darkMode.ordinal,
                            onSelectedIndexChange = { i -> vm.updateSettings { it.copy(darkMode = DarkMode.entries[i]) } },
                        )
                        SwitchPreference(
                            title = "液态玻璃效果",
                            summary = "导航栏、按钮、键盘使用实时折射玻璃（Android 12+ 模糊，13+ 折射）",
                            checked = settings.glass,
                            onCheckedChange = { v -> vm.updateSettings { it.copy(glass = v) } },
                        )
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("主题配色", fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ThemeAccent.entries.forEach { accent ->
                                    AccentSwatch(accent, selected = settings.accent == accent) {
                                        vm.updateSettings { it.copy(accent = accent) }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------- AI ----------------
                item { SmallTitle("DeepSeek AI", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "填写 DeepSeek 开放平台的 API 密钥后，即可使用拍照识别导入（${DeepSeekClient.VISION_MODEL}）与词条自动补全（${DeepSeekClient.TEXT_MODEL}）。密钥仅保存在本机。",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            TextField(
                                value = keyDraft,
                                onValueChange = { keyDraft = it.trim() },
                                label = "API 密钥（sk-…）",
                                singleLine = true,
                                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    Icon(
                                        if (showKey) MiuixIcons.Hide else MiuixIcons.Show,
                                        contentDescription = "显示/隐藏",
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .size(22.dp)
                                            .clickable { showKey = !showKey },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(
                                    text = if (aiBusy) "测试中…" else "测试连接",
                                    enabled = !aiBusy && settings.apiKey.isNotBlank() && keyDraft == settings.apiKey,
                                    onClick = vm::testApiKey,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    text = if (keyDraft == settings.apiKey && keyDraft.isNotBlank()) "已保存" else "保存密钥",
                                    enabled = keyDraft != settings.apiKey,
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    onClick = {
                                        vm.updateSettings { it.copy(apiKey = keyDraft) }
                                        vm.toast(if (keyDraft.isBlank()) "已清除密钥" else "密钥已保存")
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                // ---------------- 聆听 ----------------
                item { SmallTitle("聆听与语音", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        WindowDropdownPreference(
                            title = "环境聆听语言",
                            items = ListenLang.entries.map { it.label },
                            selectedIndex = settings.listenLang.ordinal,
                            onSelectedIndexChange = { i -> vm.setListenLang(ListenLang.entries[i]) },
                        )
                        WindowDropdownPreference(
                            title = "语音输入语言",
                            items = ListenLang.entries.map { it.label },
                            selectedIndex = settings.voiceLang.ordinal,
                            onSelectedIndexChange = { i -> vm.updateSettings { it.copy(voiceLang = ListenLang.entries[i]) } },
                        )
                        SwitchPreference(
                            title = "命中时振动",
                            summary = "环境聆听找到词条时给出触感反馈",
                            checked = settings.hapticOnHit,
                            onCheckedChange = { v -> vm.updateSettings { it.copy(hapticOnHit = v) } },
                        )
                    }
                }

                // ---------------- 数据 ----------------
                item { SmallTitle("数据（共 ${words.size} 条）", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        ArrowPreference(title = "导入 JSON 文件", onClick = actions.pickJson)
                        ArrowPreference(title = "从剪贴板导入 JSON", onClick = actions.pasteJson)
                        ArrowPreference(title = "导出为 JSON", onClick = actions.exportJson)
                        ArrowPreference(
                            title = "清空词表",
                            titleColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
                            onClick = { confirmClear = true },
                        )
                    }
                }

                // ---------------- 关于 ----------------
                item { SmallTitle("关于", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        ArrowPreference(title = "听写助手", summary = "版本 ${BuildConfig.VERSION_NAME}")
                        ArrowPreference(
                            title = "开源协议",
                            summary = "Apache License 2.0",
                            onClick = { uri.openUri("https://www.apache.org/licenses/LICENSE-2.0") },
                        )
                        ArrowPreference(
                            title = "源代码",
                            summary = "github.com/DeepShawn/DictationHelper",
                            onClick = { uri.openUri("https://github.com/DeepShawn/DictationHelper") },
                        )
                        ArrowPreference(
                            title = "致谢",
                            summary = "Miuix (compose-miuix-ui)、Backdrop 液态玻璃、pinyin4j、DeepSeek",
                            onClick = { uri.openUri("https://github.com/compose-miuix-ui/miuix") },
                        )
                    }
                }
            }
        }
    }

    ConfirmDialog(
        show = confirmClear,
        title = "清空词表",
        summary = "将删除全部 ${words.size} 条词条，且无法恢复。建议先导出备份。",
        confirmText = "清空",
        onDismiss = { confirmClear = false },
        onConfirm = vm::clearAll,
    )
}

@Composable
private fun AccentSwatch(accent: ThemeAccent, selected: Boolean, onClick: () -> Unit) {
    val brush = when {
        accent.monet -> Brush.sweepGradient(listOf(Color(0xFFE86A92), Color(0xFFF29A2E), Color(0xFF2BB673), Color(0xFF3482FF), Color(0xFF7B61FF), Color(0xFFE86A92)))
        accent.seed != null -> Brush.linearGradient(listOf(Color(accent.seed), Color(accent.seed).copy(alpha = 0.7f)))
        else -> Brush.linearGradient(listOf(Color(0xFF3482FF), Color(0xFF277AF7)))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(44.dp)
                .background(brush, CircleShape)
                .then(if (selected) Modifier.border(3.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(MiuixIcons.Ok, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(accent.label, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
