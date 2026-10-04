package com.roadsearch.openeditvideo.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadsearch.openeditvideo.model.AudioClip
import com.roadsearch.openeditvideo.model.TextOverlay
import com.roadsearch.openeditvideo.model.TrackState
import com.roadsearch.openeditvideo.model.VideoClip
import com.roadsearch.openeditvideo.model.end
import com.roadsearch.openeditvideo.ui.EditorViewModel
import com.roadsearch.openeditvideo.ui.ThumbnailStrip
import com.roadsearch.openeditvideo.ui.theme.MfColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** Video lane. [main] = thumbnail strip (media track); otherwise a compact blue bar (overlay track). */
@Composable
internal fun VideoLane(
    clips: List<VideoClip>,
    trackState: TrackState,
    selectedIds: Set<Long>,
    durationMs: Long,
    main: Boolean,
    scale: TimelineScale,
    width: Dp,
    height: Dp,
    vm: EditorViewModel,
    onSeek: (Long) -> Unit,
) {
    val locked = trackState.locked
    Box(
        Modifier
            .width(width).height(height)
            .alpha(if (trackState.hidden) .35f else if (locked) .6f else 1f)
            .pointerInput(scale, locked) {
                if (!locked) detectTapGestures { p -> onSeek(scale.dpToMs(p.x.toDp().value)) }
            },
    ) {
        clips.sortedBy { it.timelineStartMs }.forEach { clip ->
            key(clip.id) { ClipBlock(clip, durationMs, clip.id in selectedIds, main, locked, scale, vm) }
        }
        if (clips.isEmpty()) {
            Text(
                "Importez un média pour commencer",
                color = MfColors.TextMuted, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun ClipBlock(
    clip: VideoClip,
    durationMs: Long,
    selected: Boolean,
    main: Boolean,
    locked: Boolean,
    scale: TimelineScale,
    vm: EditorViewModel,
) {
    val clipMs = (clip.end(durationMs) - clip.startMs).coerceAtLeast(250L)
    val width = maxOf(scale.msToDp(clipMs), 48.dp)
    val shape = RoundedCornerShape(if (main) 10.dp else 8.dp)
    Box(
        Modifier
            .offset(x = scale.msToDp(clip.timelineStartMs))
            .width(width).fillMaxHeight().padding(vertical = 2.dp)
            .clip(shape)
            .background(if (main) MfColors.Card else TrackColors.BlueDark)
            .then(if (selected) Modifier.border(2.dp, MfColors.Violet, shape) else Modifier),
    ) {
        if (main) {
            ThumbnailStrip(clip.uri, clip.startMs, clip.end(durationMs), max(2, (width.value / 52f).toInt()), Modifier.fillMaxSize())
            Text(
                "${clipMs / 1000}s", color = Color.White, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = .5f)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(TrackColors.BlueDark, TrackColors.Blue))))
            Text(
                clip.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp),
            )
        }
        // Body: tap = select, drag = move.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(clip.id, locked) { detectTapGestures { vm.select(clip.id) } }
                .pointerInput(clip.id, scale, locked) {
                    if (!locked) detectDragGestures(
                        onDragStart = { vm.select(clip.id); vm.beginEditGesture() },
                        onDragEnd = { vm.commitEditGesture() },
                        onDragCancel = { vm.cancelEditGesture() },
                    ) { _, drag -> vm.moveClip(clip.id, scale.dpToMs(drag.x.toDp().value)) }
                },
        )
        clip.keyframes.forEach { k ->
            val x = scale.msToDp((k.timeMs - clip.startMs).coerceAtLeast(0L)).coerceIn(6.dp, width - 6.dp)
            Text("◆", color = Color.White, fontSize = 8.sp, modifier = Modifier.offset(x = x, y = 3.dp))
        }
        if (selected && !locked) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                TrimHandle(scale, { vm.beginEditGesture() }, { vm.commitEditGesture() }, { vm.cancelEditGesture() }) { vm.trimLeft(clip.id, it) }
                Spacer(Modifier.weight(1f))
                TrimHandle(scale, { vm.beginEditGesture() }, { vm.commitEditGesture() }, { vm.cancelEditGesture() }) { vm.trimRight(clip.id, it) }
            }
        }
    }
}

@Composable
private fun TrimHandle(
    scale: TimelineScale,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    onDeltaMs: (Long) -> Unit,
) {
    Box(
        Modifier
            .width(20.dp).fillMaxHeight()
            .pointerInput(scale) {
                detectDragGestures(onDragStart = { onStart() }, onDragEnd = onEnd, onDragCancel = onCancel) { _, drag ->
                    onDeltaMs(scale.dpToMs(drag.x.toDp().value))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(4.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
    }
}

@Composable
internal fun TextLane(overlays: List<TextOverlay>, scale: TimelineScale, width: Dp, height: Dp, onSeek: (Long) -> Unit) {
    Box(Modifier.width(width).height(height)) {
        overlays.sortedBy { it.startMs }.forEach { t ->
            key(t.id) {
                Box(
                    Modifier
                        .offset(x = scale.msToDp(t.startMs))
                        .width(maxOf(scale.msToDp((t.endMs - t.startMs).coerceAtLeast(0L)), 48.dp))
                        .fillMaxHeight().padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(TrackColors.PurpleDark, TrackColors.Purple)))
                        .clickable { onSeek(t.startMs) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(t.text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun AudioLane(clips: List<AudioClip>, scale: TimelineScale, width: Dp, height: Dp) {
    Box(Modifier.width(width).height(height)) {
        clips.sortedBy { it.timelineStartMs }.forEach { a ->
            key(a.id) {
                val ms = if (a.endMs > a.startMs) a.endMs - a.startMs else 3000L
                Box(
                    Modifier
                        .offset(x = scale.msToDp(a.timelineStartMs))
                        .width(maxOf(scale.msToDp(ms), 56.dp))
                        .fillMaxHeight().padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(TrackColors.GreenDark, TrackColors.Green))),
                ) {
                    Waveform(a.id, Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp))
                    Text(
                        a.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

/** Deterministic pseudo-waveform (placeholder until real peaks are extracted). Capped at ~4000 bars per clip. */
@Composable
private fun Waveform(seed: Long, modifier: Modifier) {
    Canvas(modifier) {
        val barW = 2.dp.toPx()
        val step = max(4.dp.toPx(), size.width / 4000f)
        val n = (size.width / step).toInt()
        val mid = size.height / 2f
        for (i in 0 until n) {
            val r = abs((sin((i + seed % 97) * 12.9898) * 43758.5453) % 1.0).toFloat()
            val h = size.height * (0.18f + 0.75f * r)
            drawRoundRect(
                color = Color.White.copy(alpha = .55f),
                topLeft = Offset(i * step, mid - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}
