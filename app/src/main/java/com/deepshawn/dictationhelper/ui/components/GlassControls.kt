package com.deepshawn.dictationhelper.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.dictationhelper.ui.glass.liquidGlass
import com.deepshawn.dictationhelper.ui.glass.pressScale
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class NavItem(val label: String, val icon: ImageVector)

/** 悬浮的液态玻璃底部导航栏 */
@Composable
fun GlassNavBar(
    backdrop: Backdrop,
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(64.dp)
            .liquidGlass(backdrop, Capsule())
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            NavCell(item, i == selected) { onSelect(i) }
        }
    }
}

@Composable
private fun RowScope.NavCell(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    val bg by animateColorAsState(if (selected) primary.copy(alpha = 0.16f) else Color.Transparent, label = "navBg")
    val fg by animateColorAsState(
        if (selected) primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f), label = "navFg",
    )
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .pressScale(pressed)
            .background(bg, RoundedCornerShape(50))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, contentDescription = item.label, tint = fg, modifier = Modifier.size(24.dp))
        Text(item.label, color = fg, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** 圆形 / 胶囊形的玻璃按钮 */
@Composable
fun GlassButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    tint: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .pressScale(pressed, 1.1f)
            .size(size)
            .liquidGlass(backdrop, Capsule(), tint = tint)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 玻璃面板容器 */
@Composable
fun GlassPanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.liquidGlass(backdrop, RoundedRectangle(corner))) { content() }
}

/** 小标签 */
@Composable
fun Tag(text: String, color: Color = MiuixTheme.colorScheme.primary) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
