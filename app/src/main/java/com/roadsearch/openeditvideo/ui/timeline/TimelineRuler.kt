package com.roadsearch.openeditvideo.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadsearch.openeditvideo.model.Marker
import com.roadsearch.openeditvideo.ui.formatTime
import com.roadsearch.openeditvideo.ui.theme.Inter
import com.roadsearch.openeditvideo.ui.theme.MfColors

/** Canvas-drawn ruler: one draw call instead of one composable per second, so long timelines stay at 60 FPS. */
@Composable
internal fun TimelineRuler(durationMs: Long, scale: TimelineScale, width: Dp, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val style = remember { TextStyle(color = MfColors.TextMuted, fontSize = 10.sp, fontFamily = Inter) }
    val stepSec = remember(scale) {
        listOf(1, 2, 5, 10, 15, 30, 60, 120, 300).firstOrNull { it * scale.dpPerSecond >= 56f } ?: 600
    }
    val labels = remember(durationMs, stepSec) {
        (0..(durationMs / 1000L).toInt() + 2 step stepSec).map { measurer.measure(formatTime(it * 1000L), style) }
    }
    Canvas(modifier.width(width).height(LaneHeights.Ruler)) {
        val pxPerSec = scale.dpPerSecond * density
        val minorSec = stepSec / 5f
        if (minorSec * scale.dpPerSecond >= 6f) {
            var t = 0f
            while (t * pxPerSec <= size.width) {
                val x = t * pxPerSec
                drawLine(MfColors.TextMuted.copy(alpha = .35f), Offset(x, size.height - 4.dp.toPx()), Offset(x, size.height), 1f)
                t += minorSec
            }
        }
        labels.forEachIndexed { i, layout ->
            val x = i * stepSec * pxPerSec
            drawLine(MfColors.TextMuted.copy(alpha = .75f), Offset(x, size.height - 9.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
            drawText(layout, topLeft = Offset(x + 3.dp.toPx(), 1.dp.toPx()))
        }
    }
}

@Composable
internal fun MarkerLane(
    markers: List<Marker>,
    scale: TimelineScale,
    width: Dp,
    onSeek: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    Box(Modifier.width(width).height(LaneHeights.Markers)) {
        markers.forEach { m ->
            key(m.id) {
                Box(
                    Modifier
                        .offset(x = scale.msToDp(m.positionMs) - 8.dp, y = 2.dp)
                        .size(16.dp)
                        .pointerInput(m.id, m.positionMs) {
                            detectTapGestures(onTap = { onSeek(m.positionMs) }, onLongPress = { onRemove(m.id) })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Flag, m.label.ifBlank { "Marqueur" }, tint = TrackColors.Marker, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}
