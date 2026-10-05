package com.roadsearch.openeditvideo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TrackState
import com.roadsearch.openeditvideo.model.timelineEndMs
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.theme.MfColors
import com.roadsearch.openeditvideo.ui.timeline.AudioLane
import com.roadsearch.openeditvideo.ui.timeline.ClipCapsules
import com.roadsearch.openeditvideo.ui.timeline.LayersPopover
import com.roadsearch.openeditvideo.ui.timeline.LaneHeights
import com.roadsearch.openeditvideo.ui.timeline.MarkerLane
import com.roadsearch.openeditvideo.ui.timeline.TextLane
import com.roadsearch.openeditvideo.ui.timeline.TimelineRuler
import com.roadsearch.openeditvideo.ui.timeline.TimelineScale
import com.roadsearch.openeditvideo.ui.timeline.VideoLane
import kotlinx.coroutines.flow.drop
import kotlin.math.abs
import kotlin.math.roundToInt

private val MarkerTint = Color(0xFFFFB74D)

/** Entry points shown inside the timeline itself (leading buttons, empty "ghost" tracks). */
internal class TimelineActions(
    val onImport: () -> Unit,
    val onAddMusic: () -> Unit,
    val onAddText: () -> Unit,
)

/**
 * Multi-track timeline with a FIXED playhead at the centre: the tracks scroll underneath it.
 * Scroll offset <-> playhead time are kept in sync both ways, with half a viewport of margin on each side so time 0
 * and the end can reach the centre. The leading margin hosts the "+" and mute buttons (they scroll with the content).
 */
@Composable
internal fun InteractiveTimeline(state: EditorUiState, vm: EditorViewModel, actions: TimelineActions) {
    val scale = remember(state.zoom) { TimelineScale(42f * state.zoom) }
    val duration = remember(state.clips, state.audioClips, state.textOverlays) { maxOf(state.timelineEndMs(), 5_000L) }
    val density = LocalDensity.current
    val dens = density.density
    val scroll = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }
    var expected by remember { mutableIntStateOf(-1) }
    val latest by rememberUpdatedState(state)

    val lead = with(density) { (viewportPx / 2).toDp() }
    val trackWidth = scale.msToDp(duration)
    val laneWidth = trackWidth + lead
    val totalWidth = lead + laneWidth

    fun msToPx(ms: Long): Int = (ms / 1000f * scale.dpPerSecond * dens).roundToInt()
    fun pxToMs(px: Int): Long = (px / (scale.dpPerSecond * dens) * 1000f).toLong().coerceIn(0L, duration)

    // Playhead time -> scroll offset (playback, taps, selection, zoom).
    LaunchedEffect(state.positionMs, scale, viewportPx, scroll.maxValue) {
        if (viewportPx > 0 && scroll.maxValue > 0 && !scroll.isScrollInProgress) {
            val target = msToPx(state.positionMs).coerceIn(0, scroll.maxValue)
            if (abs(scroll.value - target) > 1) {
                expected = target
                scroll.scrollTo(target)
            }
        }
    }
    // Scroll offset -> playhead time (finger drag and fling). Our own scrollTo calls are filtered by `expected`.
    LaunchedEffect(scale, duration, viewportPx) {
        snapshotFlow { scroll.value }.drop(1).collect { v ->
            if (viewportPx == 0 || scroll.maxValue == 0 || abs(v - expected) <= 1) return@collect
            if (latest.playing) vm.setPlaying(false)
            val ms = pxToMs(v)
            if (ms != latest.positionMs) vm.setPosition(ms)
        }
    }

    val trackIds = remember(state.clips) { state.clips.map { it.track }.distinct().sorted().ifEmpty { listOf(0) } }
    val selectedIds = remember(state.selectedClipId, state.selectedClipIds) { state.selectedClipIds + listOfNotNull(state.selectedClipId) }
    val selectedClip = state.clips.firstOrNull { it.id == state.selectedClipId }
    var showLayers by remember { mutableStateOf(false) }
    LaunchedEffect(selectedClip == null) { if (selectedClip == null) showLayers = false }

    Column(Modifier.fillMaxWidth().background(MfColors.Surface)) {
        TimelineToolbar(
            snapping = state.snappingEnabled, zoom = state.zoom,
            onSnap = vm::toggleSnapping, onMarker = { vm.addMarkerAtPlayhead() },
            onZoomOut = { vm.setZoom(state.zoom - .25f) }, onZoomIn = { vm.setZoom(state.zoom + .25f) },
        )
        Box(Modifier.fillMaxWidth().onSizeChanged { viewportPx = it.width }) {
            Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                Column(Modifier.width(totalWidth)) {
                    LaneRow(lead, LaneHeights.Ruler) { TimelineRuler(duration, scale, laneWidth) }
                    if (state.markers.isNotEmpty()) {
                        LaneRow(lead, LaneHeights.Markers) {
                            MarkerLane(state.markers, scale, laneWidth, onSeek = { vm.seekTo(it) }, onRemove = { vm.removeMarker(it) })
                        }
                    }
                    trackIds.forEachIndexed { i, track ->
                        val height = if (i == 0) LaneHeights.Main else LaneHeights.Overlay
                        LaneRow(
                            lead, height,
                            leading = {
                                if (i == 0) {
                                    Row(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AddButton(actions.onImport)
                                        LeadButton(
                                            if (state.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                            if (state.muted) "Muet" else "Son", vm::toggleMute,
                                        )
                                    }
                                }
                            },
                        ) {
                            VideoLane(
                                clips = remember(state.clips, track) { state.clips.filter { it.track == track } },
                                trackState = state.trackStates[track] ?: TrackState(),
                                selectedIds = selectedIds, durationMs = state.durationMs, main = i == 0,
                                scale = scale, width = laneWidth, height = height,
                                vm = vm, onSeek = { vm.seekTo(it) },
                            )
                        }
                    }
                    LaneRow(lead, LaneHeights.Text) {
                        TextLane(state.textOverlays, state.selectedTextId, scale, laneWidth, LaneHeights.Text, vm, onSeek = { vm.seekTo(it) }, onAdd = actions.onAddText)
                    }
                    LaneRow(lead, LaneHeights.Audio) {
                        AudioLane(state.audioClips, state.selectedAudioId, scale, laneWidth, LaneHeights.Audio, vm, onSeek = { vm.seekTo(it) }, onAdd = actions.onAddMusic)
                    }
                }
            }
            if (showLayers && selectedClip != null) LayersPopover(state, vm) { showLayers = false }
            // Fixed playhead.
            Canvas(Modifier.matchParentSize()) {
                val x = size.width / 2f
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                drawCircle(Color.White, 6.dp.toPx(), Offset(x, 7.dp.toPx()))
                drawCircle(MfColors.Violet, 3.dp.toPx(), Offset(x, 7.dp.toPx()))
            }
        }
        ClipCapsules(state, selectedClip, vm) { showLayers = !showLayers }
        Spacer(Modifier.height(6.dp))
    }
}

/** One timeline row: [leading margin | lane]. The margin is part of the row so its buttons stay tappable. */
@Composable
private fun LaneRow(lead: Dp, height: Dp, leading: @Composable BoxScope.() -> Unit = {}, content: @Composable () -> Unit) {
    Row(Modifier.height(height)) {
        Box(Modifier.width(lead).fillMaxHeight(), content = leading)
        content()
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, "Importer un média", tint = Color.Black, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun LeadButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.pressable(role = null, onClick = onClick).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MfColors.TextSecondary, modifier = Modifier.size(20.dp))
        Text(label, color = MfColors.TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun TimelineToolbar(
    snapping: Boolean, zoom: Float,
    onSnap: () -> Unit, onMarker: () -> Unit, onZoomOut: () -> Unit, onZoomIn: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        val chip = RoundedCornerShape(50)
        Row(
            Modifier
                .clip(chip)
                .background(if (snapping) MfColors.Active.copy(alpha = .16f) else MfColors.Card)
                .border(1.dp, if (snapping) MfColors.Active else MfColors.Outline, chip)
                .pressable(onClick = onSnap)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (snapping) MfColors.Active else MfColors.TextMuted))
            Spacer(Modifier.width(7.dp))
            Text("Aimant", color = if (snapping) MfColors.Active else MfColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.weight(1f))
        ToolbarIcon(Icons.Rounded.Flag, "Ajouter un marqueur", MarkerTint, onMarker)
        Spacer(Modifier.width(4.dp))
        ToolbarIcon(Icons.Rounded.Remove, "Zoom arrière", MfColors.TextSecondary, onZoomOut)
        Text(
            "${"%.1f".format(zoom)}×", color = MfColors.TextSecondary, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(36.dp), textAlign = TextAlign.Center,
        )
        ToolbarIcon(Icons.Rounded.Add, "Zoom avant", MfColors.TextSecondary, onZoomIn)
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(18.dp))
    }
}
