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

/*
 * MotionFlow mark traced from the brand artwork: two crossing bands forming an "S" hook (left) and a "<" (right),
 * violet -> blue -> cyan. 100x100 grid. Keep in sync with res/drawable/ic_launcher_foreground.xml.
 */
private val BandHook = listOf(43.1f to 21.8f, 16.7f to 21.8f, 16.7f to 33.2f, 87.5f to 82.4f)
private val BandTip = listOf(86.9f to 18.8f, 17.3f to 72.8f, 17.3f to 79.4f, 57.5f to 79.4f, 57.5f to 72.8f)

private val LogoViolet = Color(0xFFB245F5)
private val LogoBlue = Color(0xFF6F96FF)
private val LogoCyan = Color(0xFF1CE8E8)

/** The MotionFlow glyph, vector-drawn so it stays sharp at any size. */
@Composable
fun MotionFlowMark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 100f
        fun band(points: List<Pair<Float, Float>>) = Path().apply {
            points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) }
        }
        val brush = Brush.linearGradient(
            colors = listOf(LogoViolet, LogoBlue, LogoCyan),
            start = Offset(10f * u, 75f * u), end = Offset(90f * u, 40f * u),
        )
        val stroke = Stroke(width = 13.2f * u, cap = StrokeCap.Butt, join = StrokeJoin.Round)
        drawPath(band(BandHook), brush, style = stroke)
        drawPath(band(BandTip), brush, style = stroke)
    }
}

/** App-icon style tile: dark rounded square with a soft indigo border and the centred mark. */
@Composable
fun MotionFlowLogoTile(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val shape = RoundedCornerShape(size * 0.27f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0D1A), Color(0xFF060810))))
            .border(1.5.dp, Color(0xFF4B4C8F).copy(alpha = .85f), shape),
        contentAlignment = Alignment.Center,
    ) { MotionFlowMark(size = size * 0.62f) }
}
