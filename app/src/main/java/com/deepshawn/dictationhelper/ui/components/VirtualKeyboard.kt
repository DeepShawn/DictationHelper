package com.deepshawn.dictationhelper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.dictationhelper.ui.glass.LocalIsDark
import com.deepshawn.dictationhelper.ui.glass.pressScale
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

/**
 * 应用内置的字母虚拟键盘：直接点击输入首字母检索。
 * 英文按前缀 / 单词首字母，中文按拼音首字母匹配。
 */
@Composable
fun VirtualKeyboard(
    backdrop: Backdrop,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    GlassPanel(backdrop, modifier.fillMaxWidth()) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEachIndexed { r, row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = if (r == 1) 14.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (r == 2) {
                        Key(weight = 1.5f, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClear() }) {
                            Icon(MiuixIcons.Delete, contentDescription = "清空", modifier = Modifier.size(20.dp))
                        }
                    }
                    row.forEach { c ->
                        Key(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onKey(c) }) {
                            Text(c.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (r == 2) {
                        Key(weight = 1.5f, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onBackspace() }) {
                            Icon(MiuixIcons.Back, contentDescription = "退格", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Key(weight: Float = 1f, onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val dark = LocalIsDark.current
    val base = MiuixTheme.colorScheme.surface
    val keyColor = if (pressed) MiuixTheme.colorScheme.primary.copy(alpha = 0.3f)
    else base.copy(alpha = if (dark) 0.35f else 0.6f)
    Box(
        Modifier
            .weight(weight)
            .height(46.dp)
            .pressScale(pressed, 1.15f)
            .background(keyColor, RoundedCornerShape(10.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
