package com.roadsearch.openeditvideo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val MotionShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Layout scale derived from the available width (1.0 = 360dp). Provided by screens via BoxWithConstraints. */
val LocalUiScale = compositionLocalOf { 1f }

/** Scales a dimension with the current width so layouts hold from compact phones to tall 19.5:9 / wide screens. */
@Composable
fun Dp.sc(): Dp = this * LocalUiScale.current

fun uiScaleFor(widthDp: Float): Float = (widthDp / 360f).coerceIn(0.9f, 1.25f)

@Composable
fun MotionFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MotionDarkColorScheme, typography = MotionTypography, shapes = MotionShapes, content = content)
}
