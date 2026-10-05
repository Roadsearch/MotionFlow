package com.roadsearch.openeditvideo.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
        Modifier.size(42.dp).clip(CircleShape).pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
    }
}

/**
 * Two floating capsules that straddle the (centre) playhead, shown only while a clip is selected:
 * left = layers / keyframe / cut-start, right = cut-end / delete. Cut and keyframe need the playhead inside the clip.
 */
@Composable
internal fun ClipCapsules(state: EditorUiState, clip: VideoClip?, vm: EditorViewModel, onLayers: () -> Unit) {
    AnimatedVisibility(visible = clip != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val inside = clip?.let {
            val length = TimelineMath.duration(it, it.sourceDurationMs.coerceAtLeast(state.durationMs))
            state.positionMs > it.timelineStartMs && state.positionMs < it.timelineStartMs + length
        } ?: false
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
                Capsule {
                    CapsuleButton("Couches", onClick = onLayers) { Icon(Icons.Rounded.Layers, null, tint = Color.White) }
                    CapsuleButton("Ajouter un keyframe", enabled = inside, onClick = { vm.setKeyframeProperty() }) {
                        Text("◇+", color = Color.White.copy(alpha = if (inside) 1f else .35f), fontSize = 15.sp)
                    }
                    CapsuleButton("Couper le début ici", enabled = inside, onClick = { vm.trimStart() }) {
                        Icon(Icons.Rounded.FirstPage, null, tint = Color.White.copy(alpha = if (inside) 1f else .35f))
                    }
                }
            }
            Box(Modifier.weight(1f).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
                Capsule {
                    CapsuleButton("Couper la fin ici", enabled = inside, onClick = { vm.trimEnd() }) {
                        Icon(Icons.AutoMirrored.Rounded.LastPage, null, tint = Color.White.copy(alpha = if (inside) 1f else .35f))
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
