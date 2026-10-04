package com.roadsearch.openeditvideo.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
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
import com.roadsearch.openeditvideo.model.lengthMs
import com.roadsearch.openeditvideo.ui.EditorViewModel
import com.roadsearch.openeditvideo.ui.ThumbnailStrip
import com.roadsearch.openeditvideo.ui.theme.MfColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

private val MinClipWidth = 40.dp
private val HandleWidth = 16.dp
private val MinWidthForHandles = 80.dp

@Immutable
internal class ClipCallbacks(
    val onSelect: () -> Unit,
    val onMove: (Long) -> Unit,
    val onTrimLeft: (Long) -> Unit,
    val onTrimRight: (Long) -> Unit,
    val onStart: () -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * One clip on any lane. Tap = select. Once selected: drag the body to move it, drag a white handle to trim.
 * An unselected clip lets the drag fall through to the timeline, so the lane can still be scrolled by touching a clip.
 */
@Composable
internal fun LaneClip(
    startMs: Long,
    lengthMs: Long,
    scale: TimelineScale,
    selected: Boolean,
    locked: Boolean,
    shape: Shape,
    background: Brush,
    cb: ClipCallbacks,
    content: @Composable BoxScope.(Dp) -> Unit,
) {
    val width = maxOf(scale.msToDp(lengthMs), MinClipWidth)
    Box(
        Modifier
            .offset(x = scale.msToDp(startMs))
            .width(width).fillMaxHeight().padding(vertical = 2.dp)
            .clip(shape)
            .background(background)
            .then(if (selected) Modifier.border(2.dp, MfColors.Violet, shape) else Modifier),
    ) {
        content(width)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked) { detectTapGestures { cb.onSelect() } }
                .pointerInput(selected, locked, scale) {
                    if (selected && !locked) detectDragGestures(
                        onDragStart = { cb.onStart() },
                        onDragEnd = { cb.onEnd() },
                        onDragCancel = { cb.onCancel() },
                    ) { change, drag ->
                        change.consume()
                        cb.onMove(scale.dpToMs(drag.x.toDp().value))
                    }
                },
        )
        if (selected && !locked && width >= MinWidthForHandles) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                TrimHandle(scale, cb) { cb.onTrimLeft(it) }
                Spacer(Modifier.weight(1f))
                TrimHandle(scale, cb) { cb.onTrimRight(it) }
            }
        }
    }
}

@Composable
private fun TrimHandle(scale: TimelineScale, cb: ClipCallbacks, onDeltaMs: (Long) -> Unit) {
    Box(
        Modifier
            .width(HandleWidth).fillMaxHeight()
            .pointerInput(scale) {
                detectDragGestures(
                    onDragStart = { cb.onStart() },
                    onDragEnd = { cb.onEnd() },
                    onDragCancel = { cb.onCancel() },
                ) { change, drag ->
                    change.consume()
                    onDeltaMs(scale.dpToMs(drag.x.toDp().value))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
    }
}

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
            .pointerInput(scale) { detectTapGestures { p -> onSeek(scale.dpToMs(p.x.toDp().value)) } },
    ) {
        clips.sortedBy { it.timelineStartMs }.forEach { clip ->
            key(clip.id) {
                val clipMs = (clip.end(durationMs) - clip.startMs).coerceAtLeast(250L)
                LaneClip(
                    startMs = clip.timelineStartMs, lengthMs = clipMs, scale = scale,
                    selected = clip.id in selectedIds, locked = locked,
                    shape = RoundedCornerShape(if (main) 10.dp else 8.dp),
                    background = if (main) SolidColor(MfColors.Card) else Brush.horizontalGradient(listOf(TrackColors.BlueDark, TrackColors.Blue)),
                    cb = ClipCallbacks(
                        onSelect = { vm.select(clip.id) },
                        onMove = { vm.moveClip(clip.id, it) },
                        onTrimLeft = { vm.trimLeft(clip.id, it) },
                        onTrimRight = { vm.trimRight(clip.id, it) },
                        onStart = { vm.select(clip.id); vm.beginEditGesture() },
                        onEnd = { vm.commitEditGesture() },
                        onCancel = { vm.cancelEditGesture() },
                    ),
                ) { w ->
                    if (main) {
                        ThumbnailStrip(clip.uri, clip.startMs, clip.end(durationMs), max(2, (w.value / 52f).toInt()), Modifier.fillMaxSize())
                        Text(
                            "${clipMs / 1000}s", color = Color.White, fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 4.dp).clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = .5f)).padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    } else {
                        Text(
                            clip.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 18.dp),
                        )
                    }
                    clip.keyframes.forEach { k ->
                        val x = scale.msToDp((k.timeMs - clip.startMs).coerceAtLeast(0L)).coerceIn(6.dp, w - 6.dp)
                        Text("◆", color = Color.White, fontSize = 8.sp, modifier = Modifier.offset(x = x, y = 3.dp))
                    }
                }
            }
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
internal fun TextLane(
    overlays: List<TextOverlay>,
    selectedId: Long?,
    scale: TimelineScale,
    width: Dp,
    height: Dp,
    vm: EditorViewModel,
    onSeek: (Long) -> Unit,
) {
    Box(
        Modifier.width(width).height(height)
            .pointerInput(scale) { detectTapGestures { p -> onSeek(scale.dpToMs(p.x.toDp().value)) } },
    ) {
        overlays.sortedBy { it.startMs }.forEach { t ->
            key(t.id) {
                LaneClip(
                    startMs = t.startMs, lengthMs = (t.endMs - t.startMs).coerceAtLeast(250L), scale = scale,
                    selected = t.id == selectedId, locked = false, shape = RoundedCornerShape(8.dp),
                    background = Brush.horizontalGradient(listOf(TrackColors.PurpleDark, TrackColors.Purple)),
                    cb = ClipCallbacks(
                        onSelect = { vm.selectText(t.id) },
                        onMove = { vm.moveText(t.id, it) },
                        onTrimLeft = { vm.trimTextLeft(t.id, it) },
                        onTrimRight = { vm.trimTextRight(t.id, it) },
                        onStart = { vm.selectText(t.id); vm.beginEditGesture() },
                        onEnd = { vm.commitEditGesture() },
                        onCancel = { vm.cancelEditGesture() },
                    ),
                ) { _ ->
                    Text(
                        t.text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AudioLane(
    clips: List<AudioClip>,
    selectedId: Long?,
    scale: TimelineScale,
    width: Dp,
    height: Dp,
    vm: EditorViewModel,
    onSeek: (Long) -> Unit,
) {
    Box(
        Modifier.width(width).height(height)
            .pointerInput(scale) { detectTapGestures { p -> onSeek(scale.dpToMs(p.x.toDp().value)) } },
    ) {
        clips.sortedBy { it.timelineStartMs }.forEach { a ->
            key(a.id) {
                LaneClip(
                    startMs = a.timelineStartMs, lengthMs = a.lengthMs(), scale = scale,
                    selected = a.id == selectedId, locked = false, shape = RoundedCornerShape(8.dp),
                    background = Brush.horizontalGradient(listOf(TrackColors.GreenDark, TrackColors.Green)),
                    cb = ClipCallbacks(
                        onSelect = { vm.selectAudio(a.id) },
                        onMove = { vm.moveAudio(a.id, it) },
                        onTrimLeft = { vm.trimAudioLeft(a.id, it) },
                        onTrimRight = { vm.trimAudioRight(a.id, it) },
                        onStart = { vm.selectAudio(a.id); vm.beginEditGesture() },
                        onEnd = { vm.commitEditGesture() },
                        onCancel = { vm.cancelEditGesture() },
                    ),
                ) { _ ->
                    Waveform(a.id, Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 5.dp))
                    Text(
                        a.name, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 2.dp).width(120.dp),
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
