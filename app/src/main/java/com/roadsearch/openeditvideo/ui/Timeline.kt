package com.roadsearch.openeditvideo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TrackState
import com.roadsearch.openeditvideo.model.end
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.theme.MfColors
import com.roadsearch.openeditvideo.ui.timeline.AudioLane
import com.roadsearch.openeditvideo.ui.timeline.LabelWidth
import com.roadsearch.openeditvideo.ui.timeline.LaneHeights
import com.roadsearch.openeditvideo.ui.timeline.MarkerLane
import com.roadsearch.openeditvideo.ui.timeline.TextLane
import com.roadsearch.openeditvideo.ui.timeline.TimelineRuler
import com.roadsearch.openeditvideo.ui.timeline.TimelineScale
import com.roadsearch.openeditvideo.ui.timeline.VideoLane

private val MarkerTint = Color(0xFFFFB74D)

/**
 * Multi-track timeline: media (thumbnails), overlays, text and audio lanes under one shared horizontal scroll,
 * with a single playhead that can be scrubbed by dragging the ruler. Business logic stays in [EditorViewModel].
 */
@Composable
fun InteractiveTimeline(state: EditorUiState, vm: EditorViewModel) {
    val scale = remember(state.zoom) { TimelineScale(42f * state.zoom) }
    val duration = remember(state.durationMs, state.clips, state.audioClips, state.textOverlays) {
        val video = state.clips.maxOfOrNull { it.timelineStartMs + (it.end(state.durationMs) - it.startMs).coerceAtLeast(250L) } ?: 0L
        val audio = state.audioClips.maxOfOrNull { it.timelineStartMs + (if (it.endMs > it.startMs) it.endMs - it.startMs else 3000L) } ?: 0L
        val text = state.textOverlays.maxOfOrNull { it.endMs } ?: 0L
        maxOf(state.durationMs, video, audio, text).coerceAtLeast(5_000L)
    }
    val width = scale.msToDp(duration) + 160.dp
    val dens = LocalDensity.current.density
    val scroll = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val latest by rememberUpdatedState(state)
    val shownMs = scrubMs ?: state.positionMs
    val shown = rememberUpdatedState(shownMs)
    val trackIds = remember(state.clips) { state.clips.map { it.track }.distinct().sorted().ifEmpty { listOf(0) } }
    val selectedIds = remember(state.selectedClipId, state.selectedClipIds) {
        state.selectedClipIds + listOfNotNull(state.selectedClipId)
    }

    fun msAt(xPx: Float): Long = scale.dpToMs(xPx / dens).coerceIn(0L, duration)
    fun snapped(ms: Long): Long =
        if (latest.snappingEnabled) TimelineMath.snapToMarkers(ms, latest.markers.map { it.positionMs }) else ms

    // Keep the playhead visible while playing or scrubbing.
    LaunchedEffect(shownMs, state.playing, scrubMs != null) {
        if (viewportPx > 0 && (state.playing || scrubMs != null)) {
            val x = scale.msToDp(shownMs).value * dens
            val margin = viewportPx * 0.15f
            val left = scroll.value
            if (x > left + viewportPx - margin) scroll.scrollTo((x - viewportPx + margin).toInt())
            else if (x < left + margin) scroll.scrollTo((x - margin).toInt().coerceAtLeast(0))
        }
    }

    Column(Modifier.fillMaxWidth().background(MfColors.Surface)) {
        TimelineToolbar(
            snapping = state.snappingEnabled, zoom = state.zoom,
            onSnap = vm::toggleSnapping, onMarker = { vm.addMarkerAtPlayhead() },
            onZoomOut = { vm.setZoom(state.zoom - .25f) }, onZoomIn = { vm.setZoom(state.zoom + .25f) },
        )
        Row(Modifier.fillMaxWidth()) {
            // Fixed label column: same lane heights as the scrolling content, so rows always line up.
            Column(Modifier.width(LabelWidth)) {
                Spacer(Modifier.height(LaneHeights.Ruler))
                if (state.markers.isNotEmpty()) Spacer(Modifier.height(LaneHeights.Markers))
                trackIds.forEachIndexed { i, track ->
                    TrackLabel(track, i == 0, state.trackStates[track] ?: TrackState(), if (i == 0) LaneHeights.Main else LaneHeights.Overlay, vm)
                }
                StaticLabel(Icons.Rounded.TextFields, LaneHeights.Text)
                StaticLabel(Icons.Rounded.MusicNote, LaneHeights.Audio)
            }
            Box(Modifier.weight(1f).onSizeChanged { viewportPx = it.width }.horizontalScroll(scroll)) {
                Column(
                    Modifier.width(width).drawWithContent {
                        drawContent()
                        val x = scale.dpPerSecond * shown.value / 1000f * density
                        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                        drawCircle(Color.White, 6.dp.toPx(), Offset(x, 7.dp.toPx()))
                        drawCircle(MfColors.Violet, 3.dp.toPx(), Offset(x, 7.dp.toPx()))
                    },
                ) {
                    // Ruler = scrub zone: tap to seek, drag to scrub continuously (pauses playback).
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(scale, duration) { detectTapGestures { p -> vm.seekTo(msAt(p.x)) } }
                            .pointerInput(scale, duration) {
                                detectDragGestures(
                                    onDragStart = { p ->
                                        vm.setPlaying(false)
                                        scrubMs = snapped(msAt(p.x)); vm.seekTo(msAt(p.x))
                                    },
                                    onDragEnd = { scrubMs = null },
                                    onDragCancel = { scrubMs = null },
                                ) { change, _ ->
                                    val ms = msAt(change.position.x)
                                    scrubMs = snapped(ms); vm.seekTo(ms)
                                }
                            },
                    ) { TimelineRuler(duration, scale, width) }
                    if (state.markers.isNotEmpty()) {
                        MarkerLane(state.markers, scale, width, onSeek = { vm.seekTo(it) }, onRemove = { vm.removeMarker(it) })
                    }
                    trackIds.forEachIndexed { i, track ->
                        VideoLane(
                            clips = remember(state.clips, track) { state.clips.filter { it.track == track } },
                            trackState = state.trackStates[track] ?: TrackState(),
                            selectedIds = selectedIds, durationMs = state.durationMs, main = i == 0,
                            scale = scale, width = width,
                            height = if (i == 0) LaneHeights.Main else LaneHeights.Overlay,
                            vm = vm, onSeek = { vm.seekTo(it) },
                        )
                    }
                    TextLane(state.textOverlays, scale, width, LaneHeights.Text, onSeek = { vm.seekTo(it) })
                    AudioLane(state.audioClips, scale, width, LaneHeights.Audio)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun TimelineToolbar(
    snapping: Boolean, zoom: Float,
    onSnap: () -> Unit, onMarker: () -> Unit, onZoomOut: () -> Unit, onZoomIn: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        val chip = RoundedCornerShape(50)
        Row(
            Modifier
                .clip(chip)
                .background(if (snapping) MfColors.Cyan.copy(alpha = .16f) else MfColors.Card)
                .border(1.dp, if (snapping) MfColors.Cyan else MfColors.Outline, chip)
                .pressable(onClick = onSnap)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (snapping) MfColors.Cyan else MfColors.TextMuted))
            Spacer(Modifier.width(7.dp))
            Text("Aimant", color = if (snapping) MfColors.Cyan else MfColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
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

@Composable
private fun TrackLabel(track: Int, main: Boolean, ts: TrackState, height: Dp, vm: EditorViewModel) {
    Column(Modifier.width(LabelWidth).height(height), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            LaneIcon(
                if (ts.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                if (ts.hidden) "Afficher la piste" else "Masquer la piste",
                if (ts.hidden) MfColors.Danger else MfColors.TextSecondary,
            ) { vm.toggleTrackVisibility(track) }
            LaneIcon(
                if (ts.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                if (ts.locked) "Déverrouiller la piste" else "Verrouiller la piste",
                if (ts.locked) MfColors.Danger else MfColors.TextMuted,
            ) { vm.toggleTrackLock(track) }
        }
        if (main) {
            LaneIcon(
                if (ts.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                if (ts.muted) "Réactiver le son de la piste" else "Couper le son de la piste",
                if (ts.muted) MfColors.Danger else MfColors.TextMuted,
            ) { vm.toggleTrackMute(track) }
        }
    }
}

@Composable
private fun StaticLabel(icon: ImageVector, height: Dp) {
    Box(Modifier.width(LabelWidth).height(height), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MfColors.TextMuted, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun LaneIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(14.dp))
    }
}
