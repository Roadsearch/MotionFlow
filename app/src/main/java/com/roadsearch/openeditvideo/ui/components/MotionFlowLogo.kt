package com.roadsearch.openeditvideo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.ui.theme.MfColors

// Slanted "S" mark on a 100x100 grid. Keep in sync with res/drawable/ic_launcher_foreground.xml.
private val MarkPoints = listOf(
    76f to 27f, 40f to 27f, 26f to 50f, 74f to 50f, 60f to 73f, 24f to 73f,
)

/** The MotionFlow glyph (violet→cyan gradient stroke), vector-drawn so it stays sharp at any size. */
@Composable
fun MotionFlowMark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 100f
        val path = Path().apply {
            MarkPoints.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) }
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                listOf(MfColors.Violet, MfColors.Cyan),
                start = Offset(20f * u, 20f * u), end = Offset(80f * u, 80f * u),
            ),
            style = Stroke(width = 11f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** App-icon style tile: dark rounded square, gradient hairline border, centred mark. */
@Composable
fun MotionFlowLogoTile(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val shape = RoundedCornerShape(size * 0.27f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1A2B), Color(0xFF0E0E18))))
            .border(1.5.dp, Brush.linearGradient(listOf(MfColors.Violet.copy(alpha = .8f), MfColors.Cyan.copy(alpha = .8f))), shape),
        contentAlignment = Alignment.Center,
    ) { MotionFlowMark(size = size * 0.6f) }
}
