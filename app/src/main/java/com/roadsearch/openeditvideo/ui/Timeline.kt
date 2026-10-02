package com.roadsearch.openeditvideo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.model.*
import kotlin.math.max
import kotlin.math.sin

private val TPanel = Color(0xFF101219); private val TCard = Color(0xFF181B24); private val TAccent = Color(0xFF8B7CFF); private val TMuted = Color(0xFF8C92A3)
private val TMarker = Color(0xFFFFB74D); private val TLocked = Color(0xFFE57373)

@Composable fun InteractiveTimeline(state: EditorUiState, vm: EditorViewModel) {
    val px = 42f * state.zoom
    val duration = max(state.durationMs, state.clips.maxOfOrNull { it.timelineStartMs + (it.end(state.durationMs) - it.startMs).coerceAtLeast(1L) } ?: 0L).coerceAtLeast(1000L)
    val scroll = rememberScrollState(); val contentWidth = (duration / 1000f * px + 240f).dp
    Column(Modifier.fillMaxWidth().background(TPanel)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Timeline", color = Color.White, modifier = Modifier.weight(1f))
            Text(
                text = if (state.snappingEnabled) "Aimant ✓" else "Aimant",
                color = if (state.snappingEnabled) TAccent else TMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { vm.toggleSnapping() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { vm.addMarkerAtPlayhead() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Flag, contentDescription = "Ajouter un marqueur", tint = TMarker, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.width(6.dp))
            Text("${"%.1f".format(state.zoom)}×", color = TMuted)
        }
        Box(Modifier.fillMaxWidth().height(28.dp).horizontalScroll(scroll)) { TimeRuler(duration, px, contentWidth) }
        if (state.markers.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(20.dp).horizontalScroll(scroll)) { MarkerLane(state, vm, px, contentWidth) }
        }
        val tracks = state.clips.groupBy { it.track }.toSortedMap()
        if (tracks.isEmpty()) TimelineTrack(0, "V1", emptyList(), state, vm, px, contentWidth, scroll)
        else tracks.forEach { (track, clips) ->
            if (state.trackStates[track]?.hidden != true) {
                TimelineTrack(track, "V${track + 1}", clips, state, vm, px, contentWidth, scroll)
            }
        }
        AudioTrack(state, px, contentWidth, scroll)
        TextTrack(state, px, contentWidth, scroll)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun TimeRuler(duration: Long, px: Float, width: androidx.compose.ui.unit.Dp) { Row(Modifier.width(width).fillMaxHeight()) { repeat((duration / 1000 + 2).toInt()) { s -> Box(Modifier.width(px.dp).fillMaxHeight()) { Text(if (s % 5 == 0) "${s}s" else "·", color = TMuted) } } } }

@Composable private fun MarkerLane(state: EditorUiState, vm: EditorViewModel, px: Float, width: androidx.compose.ui.unit.Dp) {
    Box(Modifier.width(width).fillMaxHeight()) {
        state.markers.forEach { marker ->
            Box(
                Modifier
                    .offset(x = (marker.positionMs / 1000f * px).dp, y = 2.dp)
                    .size(16.dp)
                    .pointerInput(marker.id) {
                        detectTapGestures(
                            onTap = { vm.seekTo(marker.positionMs) },
                            onLongPress = { vm.removeMarker(marker.id) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Flag, contentDescription = marker.label.ifBlank { "Marqueur" }, tint = TMarker, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable private fun TrackHeader(track: Int, label: String, state: EditorUiState, vm: EditorViewModel) {
    val ts = state.trackStates[track] ?: TrackState()
    Column(Modifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TMuted)
        Row {
            Box(Modifier.size(22.dp).clickable { vm.toggleTrackLock(track) }, contentAlignment = Alignment.Center) {
                Icon(
                    if (ts.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = if (ts.locked) "Déverrouiller la piste" else "Verrouiller la piste",
                    tint = if (ts.locked) TLocked else TMuted.copy(.55f),
                    modifier = Modifier.size(12.dp),
                )
            }
            Box(Modifier.size(22.dp).clickable { vm.toggleTrackMute(track) }, contentAlignment = Alignment.Center) {
                Icon(
                    if (ts.muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    contentDescription = if (ts.muted) "Réactiver le son de la piste" else "Couper le son de la piste",
                    tint = if (ts.muted) TLocked else TMuted.copy(.55f),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Box(Modifier.size(22.dp).clickable { vm.toggleTrackVisibility(track) }, contentAlignment = Alignment.Center) {
            Icon(
                if (ts.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (ts.hidden) "Afficher la piste" else "Masquer la piste",
                tint = TMuted.copy(.55f),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable private fun TimelineTrack(track: Int, label: String, clips: List<VideoClip>, state: EditorUiState, vm: EditorViewModel, px: Float, width: androidx.compose.ui.unit.Dp, scroll: androidx.compose.foundation.ScrollState) {
    val ts = state.trackStates[track] ?: TrackState()
    Row(Modifier.fillMaxWidth().height(82.dp)) {
        TrackHeader(track, label, state, vm)
        Box(Modifier.weight(1f).height(82.dp).horizontalScroll(scroll).alpha(if (ts.locked) .55f else 1f)) {
            Box(Modifier.width(width).fillMaxHeight().pointerInput(state.zoom, state.durationMs, ts.locked) { if (!ts.locked) detectTapGestures { p -> vm.seekTo((p.x / px * 1000f).toLong().coerceAtLeast(0L)) } }) {
                clips.sortedBy { it.timelineStartMs }.forEach { clip -> TimelineClip(clip, state, vm, px, ts) }
                val x = (state.positionMs / 1000f * px).dp; Box(Modifier.offset(x = x).width(2.dp).fillMaxHeight().background(Color.White.copy(.9f)))
            }
        }
    }
}

@Composable private fun TimelineClip(clip: VideoClip, state: EditorUiState, vm: EditorViewModel, px: Float, ts: TrackState) {
    val d = (clip.end(state.durationMs) - clip.startMs).coerceAtLeast(250L); val width = (d / 1000f * px).coerceAtLeast(90f); val x = (clip.timelineStartMs / 1000f * px).dp; val selected = clip.id == state.selectedClipId || clip.id in state.selectedClipIds
    Box(Modifier.offset(x = x).width(width.dp).height(62.dp).clip(RoundedCornerShape(9.dp)).background(if (selected) TAccent.copy(.38f) else TCard).alpha(if (ts.muted) .7f else 1f)) {
        ThumbnailStrip(clip.uri, clip.startMs, clip.end(state.durationMs), max(2, (width / 52f).toInt()), Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (!ts.locked) Handle(onStart = { vm.select(clip.id); vm.beginEditGesture() }, onEnd = { vm.commitEditGesture() }, onCancel = { vm.cancelEditGesture() }) { delta -> vm.trimLeft(clip.id, (delta / px * 1000f).toLong()) }
            Box(Modifier.weight(1f).fillMaxHeight().background(if (selected) TAccent.copy(.18f) else Color.Transparent).pointerInput(clip.id, px, ts.locked) {
                if (!ts.locked) detectDragGestures(
                    onDragStart = { vm.select(clip.id); vm.beginEditGesture() },
                    onDragEnd = { vm.commitEditGesture() },
                    onDragCancel = { vm.cancelEditGesture() },
                ) { _, drag -> vm.moveClip(clip.id, (drag.x / px * 1000f).toLong()) }
                else detectTapGestures { vm.select(clip.id) }
            }) { Column(Modifier.padding(horizontal = 7.dp)) { Text(clip.name, color = Color.White, maxLines = 1); Text("${d / 1000}s", color = TMuted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) } }
            if (!ts.locked) Handle(onStart = { vm.select(clip.id); vm.beginEditGesture() }, onEnd = { vm.commitEditGesture() }, onCancel = { vm.cancelEditGesture() }) { delta -> vm.trimRight(clip.id, (delta / px * 1000f).toLong()) }
        }
        if (clip.keyframes.isNotEmpty()) {
            clip.keyframes.forEach { k ->
                val local = (k.timeMs - clip.startMs).coerceAtLeast(0L)
                val markerX = (local / 1000f * px).coerceIn(6f, width - 6f)
                Text("◆", color = Color.White, modifier = Modifier.offset(x = markerX.dp, y = 4.dp))
            }
        }
    }
}

@Composable private fun AudioTrack(state: EditorUiState, px: Float, width: androidx.compose.ui.unit.Dp, scroll: androidx.compose.foundation.ScrollState) { Row(Modifier.fillMaxWidth().height(48.dp)) { Text("A1", color = TMuted, modifier = Modifier.width(38.dp).padding(start = 8.dp, top = 15.dp)); Box(Modifier.weight(1f).height(42.dp).horizontalScroll(scroll)) { Row(Modifier.width(width).height(42.dp)) { state.audioClips.sortedBy { it.timelineStartMs }.forEach { a -> Box(Modifier.offset(x = (a.timelineStartMs / 1000f * px).dp).width(((((if (a.endMs > a.startMs) a.endMs - a.startMs else 3000L) / 1000f * px).coerceAtLeast(70f)).dp)).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(Color(0xFF245B68))) { WaveformStrip(a.id) } } } } } }

/** Symmetric pseudo-waveform, deterministic per clip so it stays stable across recompositions. */
@Composable private fun WaveformStrip(seed: Long = 0L) {
    Row(Modifier.fillMaxSize().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(28) { i ->
            val t = i / 27f
            val envelope = sin(t * Math.PI).toFloat()
            val wobble = (sin(i * 1.7 + seed % 31) * .5 + .5).toFloat()
            val h = (4 + 30 * envelope * (0.35f + 0.65f * wobble)).dp
            Box(Modifier.width(3.dp).height(h).clip(RoundedCornerShape(2.dp)).background(Color(0xFF76D5D7)))
            Spacer(Modifier.width(3.dp))
        }
    }
}

@Composable private fun TextTrack(state: EditorUiState, px: Float, width: androidx.compose.ui.unit.Dp, scroll: androidx.compose.foundation.ScrollState) { Row(Modifier.fillMaxWidth().height(42.dp)) { Text("T1", color = TMuted, modifier = Modifier.width(38.dp).padding(start = 8.dp, top = 12.dp)); Box(Modifier.weight(1f).height(38.dp).horizontalScroll(scroll)) { Row(Modifier.width(width).height(38.dp)) { state.textOverlays.sortedBy { it.startMs }.forEach { t -> Box(Modifier.offset(x = (t.startMs / 1000f * px).dp).width((((t.endMs - t.startMs) / 1000f * px).coerceAtLeast(60f)).dp).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(Color(0xFF5A3D78)), contentAlignment = Alignment.Center) { Text(t.text, color = Color.White, maxLines = 1) } } } } } }

@Composable private fun Handle(
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    onDrag: (Float) -> Unit,
) {
    Box(Modifier.width(16.dp).fillMaxHeight().pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { onStart() },
            onDragEnd = { onEnd() },
            onDragCancel = { onCancel() },
        ) { _, drag -> onDrag(drag.x) }
    }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.DragHandle, null, tint = Color.White.copy(.9f), modifier = Modifier.size(15.dp))
    }
}
