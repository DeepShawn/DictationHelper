package com.deepshawn.dictationhelper.ui.glass

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangularShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 是否开启液态玻璃效果（设置中可关闭，低端机更流畅） */
val LocalGlassEnabled = compositionLocalOf { true }

/** 是否为深色主题 */
val LocalIsDark = compositionLocalOf { false }

private val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
private val lensSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * 液态玻璃：对 [backdrop] 中已绘制的内容做模糊 + 透镜折射 + 色彩增强，并叠加一层半透明表面色。
 * 在不支持 RenderEffect 的系统上自动降级为半透明磨砂色块。
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: RoundedRectangularShape,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 6.dp,
    refractionHeight: Dp = 16.dp,
    refractionAmount: Dp = 32.dp,
): Modifier {
    val enabled = LocalGlassEnabled.current
    val dark = LocalIsDark.current
    val surface = MiuixTheme.colorScheme.surface
    val baseTint = if (tint != Color.Unspecified) tint else surface
    if (!enabled || !blurSupported) {
        val outline = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
        return this
            .clip(shape)
            .background(baseTint.copy(alpha = if (enabled) 0.88f else 0.96f), shape)
            .border(0.5.dp, outline, shape)
    }
    val surfaceAlpha = if (dark) 0.32f else 0.42f
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            if (lensSupported) lens(refractionHeight.toPx(), refractionAmount.toPx(), chromaticAberration = true)
        },
        onDrawSurface = { drawRect(baseTint.copy(alpha = surfaceAlpha)) },
    )
}

/** 按压时轻微放大的弹性效果，配合玻璃按钮 */
@Composable
fun Modifier.pressScale(pressed: Boolean, scale: Float = 1.06f): Modifier {
    val s by animateFloatAsState(if (pressed) scale else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "pressScale")
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

/**
 * 页面背景：柔和的主题色光斑，让玻璃有可折射的内容。
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val dark = LocalIsDark.current
    val base = colors.surface
    val a = colors.primary.copy(alpha = if (dark) 0.30f else 0.22f)
    val b = colors.tertiaryContainer.copy(alpha = if (dark) 0.45f else 0.55f)
    val c = colors.secondaryContainer.copy(alpha = if (dark) 0.40f else 0.60f)
    Box(modifier.fillMaxSize().background(base)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                Brush.radialGradient(listOf(a, Color.Transparent), center = Offset(w * 0.1f, h * 0.12f), radius = w * 0.75f),
                radius = w * 0.75f, center = Offset(w * 0.1f, h * 0.12f),
            )
            drawCircle(
                Brush.radialGradient(listOf(b, Color.Transparent), center = Offset(w * 0.95f, h * 0.45f), radius = w * 0.7f),
                radius = w * 0.7f, center = Offset(w * 0.95f, h * 0.45f),
            )
            drawCircle(
                Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(w * 0.2f, h * 0.9f), radius = w * 0.8f),
                radius = w * 0.8f, center = Offset(w * 0.2f, h * 0.9f),
            )
        }
    }
}
