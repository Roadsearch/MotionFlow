package com.roadsearch.openeditvideo.ui.timeline

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Time <-> distance conversion shared by every lane so they always stay aligned. */
@Immutable
class TimelineScale(val dpPerSecond: Float) {
    fun msToDp(ms: Long): Dp = (ms / 1000f * dpPerSecond).dp
    fun dpToMs(dp: Float): Long = (dp / dpPerSecond * 1000f).toLong()
    override fun equals(other: Any?) = other is TimelineScale && other.dpPerSecond == dpPerSecond
    override fun hashCode() = dpPerSecond.hashCode()
}

internal object TrackColors {
    val Blue = Color(0xFF4F6BFF)
    val BlueDark = Color(0xFF3A52D9)
    val Purple = Color(0xFFB04DEB)
    val PurpleDark = Color(0xFF8E36C9)
    val Green = Color(0xFF1FB58F)
    val GreenDark = Color(0xFF168F70)
    val Marker = Color(0xFFFFB74D)
}

internal object LaneHeights {
    val Ruler = 26.dp
    val Markers = 20.dp
    val Main = 58.dp
    val Overlay = 34.dp
    val Text = 32.dp
    val Audio = 38.dp
}

internal val LabelWidth = 44.dp
