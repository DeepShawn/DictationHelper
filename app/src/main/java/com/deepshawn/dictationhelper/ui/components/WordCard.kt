package com.deepshawn.dictationhelper.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.dictationhelper.data.EntryType
import com.deepshawn.dictationhelper.data.Lang
import com.deepshawn.dictationhelper.data.WordEntry
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun WordCard(
    entry: WordEntry,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    extraTag: String? = null,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (highlight) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainer.copy(alpha = 0.82f),
        ),
        pressFeedbackType = if (onClick != null) PressFeedbackType.Sink else PressFeedbackType.None,
        onClick = onClick,
        onLongPress = onLongPress,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.text,
                    fontSize = when (entry.type) {
                        EntryType.WORD -> 19.sp
                        EntryType.PHRASE -> 17.sp
                        EntryType.SENTENCE -> 15.sp
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.translation.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(entry.translation, fontSize = 14.sp, color = colors.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (entry.note.isNotBlank()) {
                    Text(entry.note, fontSize = 12.sp, color = colors.onSurfaceVariantSummary.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(entry.type.label)
                    Tag(entry.lang.label, color = if (entry.lang == Lang.ZH) Color(0xFFE5704B) else Color(0xFF2E9BD6))
                    if (extraTag != null) Tag(extraTag, color = Color(0xFF2BB673))
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}
