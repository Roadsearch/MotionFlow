package com.roadsearch.openeditvideo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.ui.drawers.Drawer
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.roadsearch.openeditvideo.model.*
import kotlin.math.roundToInt

internal enum class EditTab(val label: String, val icon: ImageVector) {
    EDIT("Édition", Icons.Rounded.ContentCut),
    AUDIO("Audio", Icons.Rounded.MusicNote),
    TEXT("Texte", Icons.Rounded.TextFields),
    EFFECTS("Effets", Icons.Rounded.AutoAwesome),
    OVERLAY("Superposition", Icons.Rounded.Layers),
    FILTERS("Filtres", Icons.Rounded.PhotoFilter),
}

internal class TabActions(
    val pickVideo: () -> Unit,
    val pickAudio: () -> Unit,
    val pickOverlay: () -> Unit,
    val addText: () -> Unit,
    val openSheet: (Tool) -> Unit,
    val clipVolume: () -> Unit,
    val musicVolume: () -> Unit,
    val openDrawer: (Drawer) -> Unit,
)

@Composable
internal fun TransportBar(state: EditorUiState, vm: EditorViewModel, onFullscreen: () -> Unit) {
    val end = remember(state.clips, state.audioClips, state.textOverlays) { state.timelineEndMs() }
    val canKeyframe = state.selectedClipContainsPlayhead()
    Row(
        Modifier.fillMaxWidth().background(Bg).height(46.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.undo() }, enabled = vm.canUndo()) {
                Icon(Icons.AutoMirrored.Rounded.Undo, "Annuler", tint = if (vm.canUndo()) Color.White else Muted)
            }
            IconButton({ vm.redo() }, enabled = vm.canRedo()) {
                Icon(Icons.AutoMirrored.Rounded.Redo, "Rétablir", tint = if (vm.canRedo()) Color.White else Muted)
            }
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(enabled = end > 0L) {
                if (!state.playing && state.positionMs >= end - 50L) vm.seekTo(0L)
                vm.setPlaying(!state.playing)
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.playing) "Pause" else "Lecture",
                tint = Color.White.copy(alpha = if (end > 0L) 1f else 0.38f),
                modifier = Modifier.size(34.dp),
            )
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (state.selectedClipId != null) {
                IconButton({ vm.setKeyframeProperty() }, enabled = canKeyframe) {
                    Text("◇+", color = if (canKeyframe) Color.White else Muted, fontSize = 16.sp)
                }
            }
            IconButton(onFullscreen) { Icon(Icons.Rounded.Fullscreen, "Plein écran", tint = Color.White) }
        }
    }
}

@Composable
internal fun TabBar(selected: EditTab, onSelect: (EditTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2A2A3D)))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 6.dp, bottom = 4.dp)) {
            EditTab.entries.forEach { tab ->
                val tint = if (tab == selected) Accent else Muted
                Column(
                    Modifier.weight(1f).clickable { onSelect(tab) }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, color = tint, fontSize = 10.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun ActionChip(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(76.dp).clip(shape).clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(shape)
                .background(if (selected) Accent.copy(alpha = 0.22f) else Card)
                .then(if (selected) Modifier.border(1.5.dp, Accent, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = (tint ?: if (selected) Accent else Color.White).copy(alpha = alpha), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = (if (selected) Accent else Muted).copy(alpha = alpha),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private fun blendLabel(mode: BlendMode): String = when (mode) {
    BlendMode.NORMAL -> "Normal"
    BlendMode.ADD -> "Addition"
    BlendMode.MULTIPLY -> "Multiplier"
    BlendMode.SCREEN -> "Écran"
    BlendMode.OVERLAY -> "Overlay"
    BlendMode.DARKEN -> "Assombrir"
    BlendMode.LIGHTEN -> "Éclaircir"
}

private fun maskLabel(mask: MaskSettings): String = if (!mask.enabled) "Masque" else when (mask.type) {
    MaskType.RECTANGLE -> "Rectangle"
    MaskType.CIRCLE -> "Cercle"
    MaskType.LINEAR_GRADIENT -> "Dégradé"
    MaskType.RADIAL_GRADIENT -> "Radial"
    MaskType.ELLIPSE -> "Ellipse"
}

private fun nextMask(mask: MaskSettings): MaskSettings {
    val types = MaskType.entries
    return if (!mask.enabled) mask.copy(enabled = true, type = types.first())
    else {
        val index = types.indexOf(mask.type)
        if (index >= types.lastIndex) mask.copy(enabled = false) else mask.copy(type = types[index + 1])
    }
}

private fun filterLabel(filter: VideoFilter): String = when (filter) {
    VideoFilter.NONE -> "Aucun"
    VideoFilter.CINEMATIC -> "Cinéma"
    VideoFilter.VINTAGE -> "Vintage"
    VideoFilter.COOL -> "Froid"
    VideoFilter.WARM -> "Chaud"
    VideoFilter.NOIR -> "Noir & blanc"
}

private fun filterColors(filter: VideoFilter): List<Color> = when (filter) {
    VideoFilter.NONE -> listOf(Color(0xFF2A3446), Color(0xFF3B475C))
    VideoFilter.CINEMATIC -> listOf(Color(0xFF1B3A4B), Color(0xFFE0A458))
    VideoFilter.VINTAGE -> listOf(Color(0xFF8B6B4A), Color(0xFFD9C3A0))
    VideoFilter.COOL -> listOf(Color(0xFF1E3C72), Color(0xFF2A9DF4))
    VideoFilter.WARM -> listOf(Color(0xFFB24A1D), Color(0xFFFFB347))
    VideoFilter.NOIR -> listOf(Color(0xFF000000), Color(0xFFBBBBBB))
}

@Composable
private fun FilterCard(filter: VideoFilter, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val alpha = if (enabled) 1f else 0.38f
    Column(
        Modifier.width(76.dp).clip(shape).clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(shape)
                .background(Brush.linearGradient(filterColors(filter).map { it.copy(alpha = alpha) }))
                .then(if (selected) Modifier.border(2.dp, Accent, shape) else Modifier),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            filterLabel(filter),
            color = (if (selected) Accent else Muted).copy(alpha = alpha),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun TabPanel(tab: EditTab, state: EditorUiState, vm: EditorViewModel, actions: TabActions) {
    val clip = state.selectedClip()
    val hasClip = clip != null
    Column(Modifier.fillMaxWidth().background(Panel).padding(top = 6.dp)) {
        when (tab) {
            EditTab.EDIT -> ChipRow {
                ActionChip("Importer", Icons.Rounded.AddPhotoAlternate, onClick = actions.pickVideo)
                ActionChip("Scinder", Icons.Rounded.ContentCut, hasClip) { vm.split() }
                ActionChip("Dupliquer", Icons.Rounded.ContentCopy, hasClip) { vm.duplicateSelected() }
                ActionChip("Supprimer", Icons.Rounded.DeleteOutline, hasClip || state.selectedAudioId != null || state.selectedTextId != null, tint = Color(0xFFFF8A8A)) { vm.deleteSelected() }
                ActionChip("Début", Icons.Rounded.FirstPage, hasClip) { vm.trimStart() }
                ActionChip("Fin", Icons.AutoMirrored.Rounded.LastPage, hasClip) { vm.trimEnd() }
                ActionChip("Volume", Icons.AutoMirrored.Rounded.VolumeUp, hasClip, onClick = actions.clipVolume)
                ActionChip("Transition", Icons.Rounded.SwapHoriz, hasClip) { vm.addTransition() }
                ActionChip("Marqueur", Icons.Rounded.Flag) { vm.addMarkerAtPlayhead() }
                ActionChip("Outils IA", Icons.Rounded.AutoAwesome) { actions.openDrawer(Drawer.AI) }
            }
            EditTab.AUDIO -> ChipRow {
                ActionChip("Ajouter", Icons.Rounded.MusicNote) { actions.openDrawer(Drawer.AUDIO) }
                ActionChip("Fichier", Icons.Rounded.FileUpload, onClick = actions.pickAudio)
                ActionChip(
                    "Son vidéo",
                    if (state.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    selected = state.muted,
                ) { vm.toggleMute() }
                ActionChip("Volume", Icons.AutoMirrored.Rounded.VolumeUp, state.audioClips.isNotEmpty(), onClick = actions.musicVolume)
                ActionChip("Retirer", Icons.Rounded.DeleteOutline, state.audioClips.isNotEmpty(), tint = Color(0xFFFF8A8A)) { vm.removeLastAudio() }
            }
            EditTab.TEXT -> ChipRow {
                ActionChip("Ajouter", Icons.Rounded.TextFields) { vm.clearSelection(); actions.openDrawer(Drawer.TEXT_NEW) }
                ActionChip("Style", Icons.Rounded.Palette, state.selectedTextId != null) { actions.openDrawer(Drawer.TEXT_EDIT) }
                ActionChip("Retirer", Icons.Rounded.DeleteOutline, state.textOverlays.isNotEmpty(), tint = Color(0xFFFF8A8A)) { vm.removeLastText() }
            }
            EditTab.OVERLAY -> {
                val mask = clip?.let { state.masks[it.id] } ?: MaskSettings()
                val key = clip?.let { state.chromaKeys[it.id] } ?: ChromaKeySettings()
                val blend = clip?.let { state.blendModes[it.id] } ?: BlendMode.NORMAL
                ChipRow {
                    ActionChip("Ajouter", Icons.Rounded.Layers, onClick = actions.pickOverlay)
                    ActionChip("Masque", Icons.Rounded.Crop, hasClip, mask.enabled) { actions.openDrawer(Drawer.MASK) }
                    ActionChip("Chroma", Icons.Rounded.Palette, hasClip, key.enabled) { vm.setChromaKey(key.copy(enabled = !key.enabled)) }
                    ActionChip("Animer", Icons.Rounded.Animation, hasClip) { actions.openSheet(Tool.MORE) }
                }
                ChipRow {
                    BlendMode.entries.forEach { mode ->
                        FilterChip(
                            selected = blend == mode,
                            onClick = { vm.setBlendMode(mode) },
                            enabled = hasClip,
                            label = { Text(blendLabel(mode)) },
                        )
                    }
                }
            }
            EditTab.EFFECTS -> ChipRow {
                ActionChip("Réglages", Icons.Rounded.Tune, hasClip) { actions.openSheet(Tool.EFFECTS) }
                ActionChip("Flou", Icons.Rounded.BlurOn, hasClip, state.effects.blur > 0f) {
                    vm.setEffects(blur = if (state.effects.blur > 0f) 0f else 8f)
                }
                ActionChip("Rotation", Icons.AutoMirrored.Rounded.RotateRight, hasClip) {
                    vm.setEffects(rotation = (state.effects.rotation + 90f) % 360f)
                }
                ActionChip("Animer", Icons.Rounded.Animation, hasClip) { actions.openSheet(Tool.MORE) }
                ActionChip("Réinitialiser", Icons.Rounded.RestartAlt, hasClip) {
                    vm.setEffects(0f, 0f, 1f, 0f, 0f, 0f, VideoFilter.NONE)
                }
            }
            EditTab.FILTERS -> ChipRow {
                VideoFilter.entries.forEach { filter ->
                    FilterCard(filter, state.effects.filter == filter, hasClip) { vm.setEffects(filter = filter) }
                }
            }
        }
    }
}

@Composable
internal fun VolumeDialog(title: String, initial: Float, close: () -> Unit, apply: (Float) -> Unit) {
    var value by remember { mutableFloatStateOf(initial.coerceIn(0f, 2f)) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(title) },
        text = {
            Column {
                Text("${(value * 100).roundToInt()} %")
                Slider(value, { value = it }, valueRange = 0f..2f)
            }
        },
        confirmButton = { TextButton({ apply(value); close() }) { Text("Appliquer") } },
        dismissButton = { TextButton(close) { Text("Annuler") } },
    )
}

/** Joue les pistes audio importées pendant l'aperçu, synchronisées avec la tête de lecture. */
@Composable
internal fun AudioPreview(state: EditorUiState) {
    val context = LocalContext.current
    state.audioClips.forEach { audio ->
        key(audio.id) {
            val player = remember {
                ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(audio.uri)); prepare() }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            val length = (audio.endMs - audio.startMs).coerceAtLeast(1L)
            val local = state.positionMs - audio.timelineStartMs
            val inRange = local >= 0L && local < length
            LaunchedEffect(state.playing, state.seekNonce, audio.volume, state.muted, inRange) {
                player.volume = if (state.muted) 0f else audio.volume.coerceIn(0f, 2f)
                if (state.playing && inRange) {
                    val target = audio.startMs + local
                    if (kotlin.math.abs(player.currentPosition - target) > 250L) player.seekTo(target)
                    player.play()
                } else {
                    player.pause()
                }
            }
        }
    }
}
