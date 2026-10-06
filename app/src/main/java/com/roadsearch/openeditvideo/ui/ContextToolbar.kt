package com.roadsearch.openeditvideo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoFilter
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.ChromaKeySettings
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.MaskSettings
import com.roadsearch.openeditvideo.model.VideoClip
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.drawers.Drawer
import com.roadsearch.openeditvideo.ui.theme.MfColors

/** What is selected decides which tools the bottom bar offers. */
internal enum class EditMode { NONE, MAIN_CLIP, OVERLAY_CLIP, TEXT, AUDIO }

internal fun EditorUiState.editMode(): EditMode {
    if (selectedTextId != null) return EditMode.TEXT
    if (selectedAudioId != null) return EditMode.AUDIO
    val id = selectedClipId ?: return EditMode.NONE
    val clip = clips.firstOrNull { it.id == id } ?: return EditMode.NONE
    return if (clip.track == clips.minOf { it.track }) EditMode.MAIN_CLIP else EditMode.OVERLAY_CLIP
}

private class ToolItem(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val tint: Color? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

private val Danger = Color(0xFFFF8A8A)

private fun clipAtPlayhead(state: EditorUiState): VideoClip? {
    val main = state.clips.minOfOrNull { it.track } ?: return null
    return state.clips.filter { it.track == main }.firstOrNull { c ->
        val length = TimelineMath.duration(c, c.sourceDurationMs.coerceAtLeast(state.durationMs))
        state.positionMs >= c.timelineStartMs && state.positionMs < c.timelineStartMs + length
    } ?: state.clips.firstOrNull()
}

private fun rootItems(state: EditorUiState, vm: EditorViewModel, a: TabActions) = listOf(
    ToolItem("Modifier", Icons.Rounded.ContentCut, state.clips.isNotEmpty()) { clipAtPlayhead(state)?.let { vm.select(it.id) } },
    ToolItem("Importer", Icons.Rounded.AddPhotoAlternate, onClick = a.pickVideo),
    ToolItem("Musique", Icons.Rounded.MusicNote) { a.openDrawer(Drawer.AUDIO) },
    ToolItem("Texte", Icons.Rounded.TextFields) { vm.clearSelection(); a.openDrawer(Drawer.TEXT_NEW) },
    ToolItem("Superposer", Icons.Rounded.Layers, onClick = a.pickOverlay),
    ToolItem("Outils IA", Icons.Rounded.AutoAwesome) { a.openDrawer(Drawer.AI) },
    ToolItem("Marqueur", Icons.Rounded.Flag) { vm.addMarkerAtPlayhead() },
)

private fun clipItems(state: EditorUiState, vm: EditorViewModel, a: TabActions, overlay: Boolean): List<ToolItem> {
    val clip = state.clips.firstOrNull { it.id == state.selectedClipId } ?: return emptyList()
    val key = state.chromaKeys[clip.id] ?: ChromaKeySettings()
    val mask = state.masks[clip.id] ?: MaskSettings()
    val track = state.trackStates[clip.track]
    return buildList {
        add(ToolItem("Diviser", Icons.Rounded.ContentCut) { vm.split() })
        add(ToolItem("Dupliquer", Icons.Rounded.ContentCopy) { vm.duplicateSelected() })
        add(ToolItem("Supprimer", Icons.Rounded.DeleteOutline, tint = Danger) { vm.deleteSelected() })
        add(ToolItem("Début", Icons.Rounded.FirstPage) { vm.trimStart() })
        add(ToolItem("Fin", Icons.AutoMirrored.Rounded.LastPage) { vm.trimEnd() })
        add(ToolItem("Volume", Icons.AutoMirrored.Rounded.VolumeUp, onClick = a.clipVolume))
        add(ToolItem("Régler", Icons.Rounded.Tune) { a.openSheet(com.roadsearch.openeditvideo.model.Tool.EFFECTS) })
        add(ToolItem("Filtre", Icons.Rounded.PhotoFilter) { a.openDrawer(Drawer.FILTERS) })
        add(ToolItem("Animation", Icons.Rounded.Animation) { a.openSheet(com.roadsearch.openeditvideo.model.Tool.MORE) })
        add(ToolItem("Masque", Icons.Rounded.Crop, selected = mask.enabled) { a.openDrawer(Drawer.MASK) })
        add(ToolItem("Fond vert", Icons.Rounded.Palette, selected = key.enabled) { vm.setChromaKey(key.copy(enabled = !key.enabled)) })
        if (!overlay) add(ToolItem("Transition", Icons.Rounded.SwapHoriz) { vm.addTransition() })
        if (overlay) {
            add(ToolItem("Mélanger", Icons.Rounded.Layers) { a.openDrawer(Drawer.MASK) })
            add(
                ToolItem(
                    if (track?.hidden == true) "Afficher" else "Masquer",
                    if (track?.hidden == true) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    selected = track?.hidden == true,
                ) { vm.toggleTrackVisibility(clip.track) },
            )
            add(
                ToolItem(
                    if (track?.locked == true) "Déverrouiller" else "Verrouiller",
                    if (track?.locked == true) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    selected = track?.locked == true,
                ) { vm.toggleTrackLock(clip.track) },
            )
        }
    }
}

private fun textItems(state: EditorUiState, vm: EditorViewModel, a: TabActions) = listOf(
    ToolItem("Style", Icons.Rounded.Palette) { a.openDrawer(Drawer.TEXT_EDIT) },
    ToolItem("Ajouter", Icons.Rounded.TextFields) { vm.clearSelection(); a.openDrawer(Drawer.TEXT_NEW) },
    ToolItem("Supprimer", Icons.Rounded.DeleteOutline, tint = Danger) { vm.deleteSelected() },
)

private fun audioItems(state: EditorUiState, vm: EditorViewModel, a: TabActions) = listOf(
    ToolItem("Volume", Icons.AutoMirrored.Rounded.VolumeUp, onClick = a.musicVolume),
    ToolItem("Ajouter", Icons.Rounded.MusicNote) { a.openDrawer(Drawer.AUDIO) },
    ToolItem("Supprimer", Icons.Rounded.DeleteOutline, tint = Danger) { vm.deleteSelected() },
)

/**
 * Two-level bottom bar. Nothing selected: boxed root tiles. Something selected: the tools of that element, plus a
 * fixed chevron on the left that collapses back to the root level (by clearing the selection).
 */
@Composable
internal fun ContextToolbar(state: EditorUiState, vm: EditorViewModel, actions: TabActions) {
    val mode = state.editMode()
    Column(Modifier.fillMaxWidth().background(Panel).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2A2A3D)))
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "toolbar",
        ) { m ->
            Row(Modifier.fillMaxWidth().height(76.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (m != EditMode.NONE) {
                    Box(
                        Modifier.padding(start = 10.dp, end = 4.dp).size(width = 44.dp, height = 56.dp)
                            .clip(RoundedCornerShape(10.dp)).background(MfColors.CardHigh)
                            .pressable(role = null) { vm.clearSelection() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.KeyboardArrowDown, "Replier", tint = Color.White) }
                }
                val items = when (m) {
                    EditMode.NONE -> rootItems(state, vm, actions)
                    EditMode.MAIN_CLIP -> clipItems(state, vm, actions, overlay = false)
                    EditMode.OVERLAY_CLIP -> clipItems(state, vm, actions, overlay = true)
                    EditMode.TEXT -> textItems(state, vm, actions)
                    EditMode.AUDIO -> audioItems(state, vm, actions)
                }
                ToolRow(items, boxed = m == EditMode.NONE, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolRow(items: List<ToolItem>, boxed: Boolean, modifier: Modifier) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (boxed) 8.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { items.forEach { ToolTile(it, boxed) } }
        // Hint that more tools are hidden to the right.
        if (scroll.canScrollForward) {
            Box(
                Modifier.align(Alignment.CenterEnd).width(30.dp).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Panel))),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun ToolTile(item: ToolItem, boxed: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    val tint = item.tint ?: if (item.selected) Accent else Color.White
    Column(
        Modifier.width(if (boxed) 76.dp else 68.dp).clip(shape)
            .then(if (boxed) Modifier.background(MfColors.Card) else Modifier)
            .pressable(enabled = item.enabled, onClick = item.onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(item.icon, null, tint = tint.copy(alpha = if (item.enabled) 1f else .38f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            item.label, color = tint.copy(alpha = if (item.enabled) 1f else .38f), fontSize = 10.sp,
            maxLines = 2, textAlign = TextAlign.Center, lineHeight = 12.sp,
        )
    }
}

/** True when a clip is selected and the playhead lies strictly inside it (cuts and keyframes need that). */
internal fun EditorUiState.selectedClipContainsPlayhead(): Boolean {
    val clip = clips.firstOrNull { it.id == selectedClipId } ?: return false
    val length = TimelineMath.duration(clip, clip.sourceDurationMs.coerceAtLeast(durationMs))
    return positionMs > clip.timelineStartMs && positionMs < clip.timelineStartMs + length
}
