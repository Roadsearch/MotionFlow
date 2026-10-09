package com.roadsearch.openeditvideo.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import com.roadsearch.openeditvideo.ui.editMode
import com.roadsearch.openeditvideo.ui.EditMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.VideoClip
import com.roadsearch.openeditvideo.ui.EditorViewModel
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.theme.MfColors

@Composable
private fun Capsule(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(MfColors.CardHigh).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

@Composable
private fun CapsuleButton(description: String, enabled: Boolean = true, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).pressable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
    }
}

/** "Couper et supprimer à gauche / à droite" glyph: playhead bar plus a dashed box on the part that is removed. */
@Composable
private fun SplitDeleteIcon(left: Boolean, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val sw = 2.dp.toPx()
        val bar = if (left) w * 0.64f else w * 0.36f
        drawLine(tint, Offset(bar, h * 0.06f), Offset(bar, h * 0.94f), sw)
        val x0 = if (left) w * 0.06f else bar + w * 0.1f
        val x1 = if (left) bar - w * 0.1f else w * 0.94f
        drawRoundRect(
            tint, Offset(x0, h * 0.2f), Size(x1 - x0, h * 0.6f), CornerRadius(2.dp.toPx()),
            style = Stroke(width = sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(sw * 1.5f, sw * 1.5f))),
        )
    }
}

/** "Étirer jusqu'au début / à la fin" glyph: edge bar with an arrow pointing at it. */
@Composable
private fun StretchIcon(toStart: Boolean, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val sw = 2.dp.toPx()
        val edge = if (toStart) w * 0.1f else w * 0.9f
        val dir = if (toStart) 1f else -1f
        drawLine(tint, Offset(edge, h * 0.12f), Offset(edge, h * 0.88f), sw)
        drawLine(tint, Offset(edge + dir * w * 0.12f, h * 0.5f), Offset(edge + dir * w * 0.78f, h * 0.5f), sw)
        drawLine(tint, Offset(edge + dir * w * 0.12f, h * 0.5f), Offset(edge + dir * w * 0.32f, h * 0.3f), sw)
        drawLine(tint, Offset(edge + dir * w * 0.12f, h * 0.5f), Offset(edge + dir * w * 0.32f, h * 0.7f), sw)
    }
}

/**
 * Two floating capsules that straddle the (centre) playhead while a clip is selected.
 * Main clip:  left [couches | keyframe | couper+supprimer à gauche]   right [couper+supprimer à droite | supprimer].
 * Overlay:    left [couches | keyframe | étirer au début | couper+supprimer à gauche]
 *             right [couper+supprimer à droite | étirer à la fin | supprimer].
 * Keyframe and cut buttons need the playhead inside the clip; stretch buttons need room to stretch.
 */
@Composable
internal fun ClipCapsules(state: EditorUiState, clip: VideoClip?, vm: EditorViewModel, onLayers: () -> Unit) {
    AnimatedVisibility(visible = clip != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val overlay = state.editMode() == EditMode.OVERLAY_CLIP
        val length = clip?.let { TimelineMath.duration(it, it.sourceDurationMs.coerceAtLeast(state.durationMs)) } ?: 0L
        val inside = clip != null && state.positionMs > clip.timelineStartMs && state.positionMs < clip.timelineStartMs + length
        val main = state.clips.minOfOrNull { it.track }
        val mainEnd = state.clips.filter { it.track == main }
            .maxOfOrNull { it.timelineStartMs + TimelineMath.duration(it, it.sourceDurationMs.coerceAtLeast(state.durationMs)) } ?: 0L
        val canStretchStart = clip != null && clip.timelineStartMs > 0L
        val canStretchEnd = clip != null && clip.timelineStartMs + length < mainEnd
        fun tint(enabled: Boolean) = Color.White.copy(alpha = if (enabled) 1f else .35f)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
                Capsule {
                    CapsuleButton("Couches", onClick = onLayers) { Icon(Icons.Rounded.Layers, null, tint = Color.White) }
                    CapsuleButton("Ajouter une image clé", enabled = inside, onClick = { vm.setKeyframeProperty() }) {
                        Text("◇+", color = tint(inside), fontSize = 15.sp)
                    }
                    if (overlay) {
                        CapsuleButton("Étirer jusqu'au début", enabled = canStretchStart, onClick = { vm.stretchToStart() }) { StretchIcon(true, tint(canStretchStart)) }
                    }
                    CapsuleButton("Couper et supprimer à gauche", enabled = inside, onClick = { vm.trimStart() }) { SplitDeleteIcon(true, tint(inside)) }
                }
            }
            Box(Modifier.weight(1f).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
                Capsule {
                    CapsuleButton("Couper et supprimer à droite", enabled = inside, onClick = { vm.trimEnd() }) { SplitDeleteIcon(false, tint(inside)) }
                    if (overlay) {
                        CapsuleButton("Étirer jusqu'à la fin", enabled = canStretchEnd, onClick = { vm.stretchToEnd() }) { StretchIcon(false, tint(canStretchEnd)) }
                    }
                    CapsuleButton("Supprimer", onClick = { vm.deleteSelected() }) { Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFF8A8A)) }
                }
            }
        }
    }
}

/** "Ajuster la couche": non-modal popover glued to the left edge. Top of the list = front layer. Drag ☰ to reorder. */
@Composable
internal fun BoxScope.LayersPopover(state: EditorUiState, vm: EditorViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val tracks = remember(state.clips) { state.clips.map { it.track }.distinct().sortedDescending() }
    val main = tracks.lastOrNull()
    val selectedTrack = state.clips.firstOrNull { it.id == state.selectedClipId }?.track
    Column(
        Modifier.align(Alignment.TopStart).width(176.dp)
            .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)).background(MfColors.CardHigh).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ajuster la couche", color = MfColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Close, "Fermer", tint = MfColors.TextSecondary, modifier = Modifier.size(18.dp).clickable(onClick = onClose))
        }
        tracks.forEachIndexed { index, track ->
            val first = state.clips.filter { it.track == track }.minByOrNull { it.timelineStartMs }
            val isSelected = track == selectedTrack
            var swapped by remember { mutableStateOf(false) }
            var acc by remember { mutableStateOf(0f) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(width = 104.dp, height = 58.dp).clip(RoundedCornerShape(8.dp)).background(MfColors.Card)
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color.White else MfColors.Outline, RoundedCornerShape(8.dp))
                        .clickable { first?.let { vm.select(it.id) } },
                ) {
                    val uri = first?.uri
                    if (uri != null) {
                        val request = remember(uri) { ImageRequest.Builder(context).data(uri).videoFrameMillis(0L).build() }
                        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    if (track == main) {
                        Text(
                            "Principal", color = Color.White, fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.TopStart).padding(3.dp).clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = .55f)).padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.Check, null, tint = Color.Black,
                            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(16.dp).clip(CircleShape).background(Color.White),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Menu, "Réordonner", tint = MfColors.TextSecondary,
                    modifier = Modifier.size(28.dp).padding(4.dp).pointerInput(tracks, track) {
                        detectDragGestures(
                            onDragEnd = { swapped = false; acc = 0f },
                            onDragCancel = { swapped = false; acc = 0f },
                        ) { change, drag ->
                            change.consume()
                            acc += drag.y
                            if (!swapped && kotlin.math.abs(acc) > 40.dp.toPx()) {
                                val neighbour = tracks.getOrNull(if (acc > 0) index + 1 else index - 1)
                                if (neighbour != null) vm.swapTracks(track, neighbour)
                                swapped = true
                            }
                        }
                    },
                )
            }
        }
    }
}
