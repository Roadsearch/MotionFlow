package com.roadsearch.openeditvideo.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.roadsearch.openeditvideo.model.*
import com.roadsearch.openeditvideo.ui.theme.MfColors
import com.roadsearch.openeditvideo.ui.drawers.Drawer
import com.roadsearch.openeditvideo.ui.drawers.EditorDrawers
import com.roadsearch.openeditvideo.core.TimelineMath
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

// Aligned on the MotionFlow design tokens (ui/theme/Color.kt).
internal val Bg = MfColors.Background
internal val Panel = MfColors.Surface
internal val Card = MfColors.Card
internal val Muted = MfColors.TextSecondary
internal val Accent = MfColors.Active

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit = {}) {
    val state by vm.state.collectAsState(); val context = LocalContext.current
    var more by remember { mutableStateOf(false) }; var sheet by remember { mutableStateOf<Tool?>(null) }; var drawer by remember { mutableStateOf<Drawer?>(null) }; var textDialog by remember { mutableStateOf(false) }; var tab by remember { mutableStateOf(EditTab.EDIT) }; var clipVolumeDialog by remember { mutableStateOf(false) }; var musicVolumeDialog by remember { mutableStateOf(false) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.import(u, displayName(context, u) ?: "Video") } }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.import(u, displayName(context, u) ?: "Audio", true) } }
    val overlayPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.importOverlay(u, displayName(context, u) ?: "Overlay") } }
    fun pickVideo() { videoPicker.launch(arrayOf("video/*", "image/*")) }
    fun pickOverlay() { overlayPicker.launch(arrayOf("video/*", "image/*")) }
    fun pickAudio() { audioPicker.launch(arrayOf("audio/*")) }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()) {
        TopBar(onBack, { pickVideo() }, { drawer = Drawer.EXPORT }, more, { more = it }, state.clips.any { it.track == 0 })
        Preview(context, state, vm) { pickVideo() }
        TransportBar(state, vm)
        Timeline(state, vm)
        if (state.exportProgress != null || state.exportMessage != null) {
            ExportBanner(state.exportProgress, state.exportMessage, vm)
        }
        TabPanel(tab, state, vm, TabActions(
            pickVideo = { pickVideo() }, pickAudio = { pickAudio() }, pickOverlay = { pickOverlay() },
            addText = { textDialog = true }, openSheet = { sheet = it },
            clipVolume = { clipVolumeDialog = true }, musicVolume = { musicVolumeDialog = true },
            openDrawer = { drawer = it },
        ))
        TabBar(tab) { tab = it }
        AudioPreview(state)
    }}

    EditorDrawers(drawer, { drawer = null }, state, vm, pickAudioFile = { pickAudio() })

    sheet?.let { tool ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Panel) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { when (tool) {
                Tool.AUDIO -> { SheetTitle("Audio"); SheetAction("Ajouter une piste audio", Icons.Rounded.MusicNote) { pickAudio(); sheet = null }; SheetAction("Couper le son de la vidéo", Icons.AutoMirrored.Rounded.VolumeOff) { vm.toggleMute(); sheet = null } }
                Tool.TEXT -> { SheetTitle("Texte"); SheetAction("Ajouter un texte à la tête de lecture", Icons.Rounded.TextFields) { textDialog = true; sheet = null } }
                Tool.EFFECTS, Tool.ADJUST -> EffectsPanel(state, vm)
                Tool.MORE -> AnimationPanel(vm)
                else -> { SheetTitle("Actions du clip"); SheetAction("Scinder ici", Icons.Rounded.ContentCut) { vm.split(); sheet = null }; SheetAction("Rogner le début", Icons.Rounded.FirstPage) { vm.trimStart(); sheet = null }; SheetAction("Rogner la fin", Icons.AutoMirrored.Rounded.LastPage) { vm.trimEnd(); sheet = null }; SheetAction("Supprimer", Icons.Rounded.DeleteOutline) { vm.deleteSelected(); sheet = null } }
            } }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
    if (textDialog) TextDialog({ textDialog = false }, vm::addText)
    if (clipVolumeDialog) state.selectedClip()?.let { c -> VolumeDialog("Volume du clip", c.volume, { clipVolumeDialog = false }) { vm.setSelectedVolume(it) } }
    if (musicVolumeDialog) VolumeDialog("Volume de la musique", state.audioClips.firstOrNull()?.volume ?: 1f, { musicVolumeDialog = false }) { vm.setAudioVolume(it) }
}


@Composable
private fun ExportBanner(progress: Float?, message: String?, vm: EditorViewModel) {
    val running = progress != null && progress < 0.999f
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Card),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(message ?: "Export vidéo", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    if (progress != null) {
                        Text("${(progress * 100f).roundToInt()}%", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (running) {
                    TextButton(onClick = vm::cancelExport) { Text("Annuler") }
                } else {
                    TextButton(onClick = vm::clearExportMessage) { Text("Fermer") }
                }
            }
            if (running) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable private fun TopBar(onBack: () -> Unit, onImport: () -> Unit, onExport: () -> Unit, more: Boolean, setMore: (Boolean) -> Unit, canExport: Boolean) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Card).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("1080p · 30", color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        Box {
            IconButton({ setMore(true) }) { Icon(Icons.Rounded.MoreVert, "Plus", tint = Color.White) }
            DropdownMenu(more, { setMore(false) }) {
                DropdownMenuItem({ Text("Importer un média") }, leadingIcon = { Icon(Icons.Rounded.VideoLibrary, null) }, onClick = { setMore(false); onImport() })
                DropdownMenuItem({ Text("Exporter") }, leadingIcon = { Icon(Icons.Rounded.FileUpload, null) }, enabled = canExport, onClick = { setMore(false); onExport() })
            }
        }
        Box(
            Modifier.height(34.dp).clip(RoundedCornerShape(10.dp))
                .background(
                    if (canExport) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF6A4DEB), Color(0xFF8B5CF6)))
                    else androidx.compose.ui.graphics.SolidColor(Card),
                )
                .clickable(enabled = canExport, onClick = onExport)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Exporter", color = if (canExport) Color.White else Muted, style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.width(8.dp))
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun ColumnScope.Preview(context: Context, state: EditorUiState, vm: EditorViewModel, onImport: () -> Unit) {
    val latest by rememberUpdatedState(state)
    val mainTrack = remember(state.clips) { state.clips.minOfOrNull { it.track } }
    // The preview shows the clip under the playhead on the lowest video track (not just the selected one).
    val clip = remember(state.clips, state.positionMs, state.durationMs, mainTrack) {
        state.clips.filter { it.track == mainTrack }.firstOrNull { c ->
            val length = TimelineMath.duration(c, c.sourceDurationMs.coerceAtLeast(state.durationMs))
            state.positionMs >= c.timelineStartMs && state.positionMs < c.timelineStartMs + length
        }
    }

    // Master clock: advances the playhead through clips, gaps, audio-only and text-only sections alike.
    // Players follow the clock; they no longer overwrite the position (that made scrubbing impossible).
    LaunchedEffect(state.playing) {
        if (!state.playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        var accNs = 0L
        while (isActive) {
            val now = withFrameNanos { it }
            accNs += now - last
            last = now
            if (accNs < 33_000_000L) continue
            val ms = accNs / 1_000_000L
            accNs -= ms * 1_000_000L
            val total = latest.timelineEndMs()
            val next = latest.positionMs + ms
            if (next >= total) { vm.setPosition(total); vm.setPlaying(false); break }
            vm.setPosition(next)
        }
    }

    Box(
        Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (state.clips.isEmpty()) {
            EmptyPreview(onImport)
        } else if (clip != null) key(clip.id) {
            val player = remember { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(clip.uri)); prepare() } }
            DisposableEffect(player) { onDispose { player.release() } }
            fun localTarget(): Long {
                val cur = latest.clips.firstOrNull { it.id == clip.id } ?: clip
                return (cur.startMs + (latest.positionMs - cur.timelineStartMs)).coerceAtLeast(0L)
            }
            LaunchedEffect(player, state.seekNonce, state.playing) {
                val target = localTarget()
                if (kotlin.math.abs(player.currentPosition - target) > 120L) player.seekTo(target)
                player.playWhenReady = latest.playing
            }
            LaunchedEffect(player, state.playing) {
                if (state.playing) while (isActive) {
                    delay(400)
                    val target = localTarget()
                    if (kotlin.math.abs(player.currentPosition - target) > 350L) player.seekTo(target)
                }
            }
            LaunchedEffect(state.muted, clip.id, clip.effects, clip.animation, state.chromaKeys[clip.id]) {
                player.volume = if (state.muted) 0f else clip.volume.coerceIn(0f, 2f)
                player.setVideoEffects(vm.previewEffects(clip))
            }
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable private fun EmptyPreview(onImport: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Card).clickable(onClick = onImport), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Accent, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(10.dp))
            Text("Appuyer pour importer une vidéo", color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable private fun Timeline(state: EditorUiState, vm: EditorViewModel) { InteractiveTimeline(state, vm) }


@Composable private fun EffectsPanel(state: EditorUiState, vm: EditorViewModel) {
    val clip = state.selectedClip()
    val localTime = clip?.let { (state.positionMs - it.timelineStartMs + it.startMs).coerceAtLeast(it.startMs) } ?: 0L
    val keyframe = clip?.keyframesAt(state.positionMs) ?: Keyframe(localTime)
    SheetTitle("Animation & effets")
    if (clip != null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Keyframe · ${formatTime(localTime)}", color = Color.White, modifier = Modifier.weight(1f))
            IconButton({ vm.setKeyframeProperty(x=keyframe.x,y=keyframe.y,scale=keyframe.scale,rotation=keyframe.rotation,opacity=keyframe.opacity) }) { Icon(Icons.Rounded.AddCircle, "Ajouter un keyframe", tint = Accent) }
            IconButton({ vm.removeKeyframeAtPlayhead() }) { Icon(Icons.Rounded.DeleteOutline, "Supprimer", tint = Muted) }
        }
        Text("Position X ${keyframe.x.roundToInt()} px", color=Color.White, modifier=Modifier.padding(horizontal=20.dp)); Slider(keyframe.x, { vm.setKeyframeProperty(x=it) }, valueRange=-500f..500f, modifier=Modifier.padding(horizontal=16.dp))
        Text("Position Y ${keyframe.y.roundToInt()} px", color=Color.White, modifier=Modifier.padding(horizontal=20.dp)); Slider(keyframe.y, { vm.setKeyframeProperty(y=it) }, valueRange=-500f..500f, modifier=Modifier.padding(horizontal=16.dp))
        Text("Échelle ${(keyframe.scale*100).roundToInt()}%", color=Color.White, modifier=Modifier.padding(horizontal=20.dp)); Slider(keyframe.scale, { vm.setKeyframeProperty(scale=it) }, valueRange=.25f..3f, modifier=Modifier.padding(horizontal=16.dp))
        Text("Rotation ${keyframe.rotation.roundToInt()}°", color=Color.White, modifier=Modifier.padding(horizontal=20.dp)); Slider(keyframe.rotation, { vm.setKeyframeProperty(rotation=it) }, valueRange=-180f..180f, modifier=Modifier.padding(horizontal=16.dp))
        Text("Opacité ${(keyframe.opacity*100).roundToInt()}%", color=Color.White, modifier=Modifier.padding(horizontal=20.dp)); Slider(keyframe.opacity, { vm.setKeyframeProperty(opacity=it) }, valueRange=0f..1f, modifier=Modifier.padding(horizontal=16.dp))
        Text("Les keyframes sont interpolés en temps réel dans la preview.", color=Muted, style=MaterialTheme.typography.labelSmall, modifier=Modifier.padding(horizontal=20.dp, vertical=4.dp))
    }
    HorizontalDivider(Modifier.padding(vertical=8.dp), color=Card)
    Text("Effets Media3", color=Color.White, style=MaterialTheme.typography.titleMedium, modifier=Modifier.padding(horizontal=20.dp)); Text("Rotation ${state.effects.rotation.roundToInt()}°", color=Color.White, modifier=Modifier.padding(horizontal=20.dp)); Slider(state.effects.rotation, { vm.setEffects(rotation=it) }, valueRange=-180f..180f, modifier=Modifier.padding(horizontal=16.dp)); Text("Contraste ${(state.effects.contrast*100).roundToInt()}%", color=Color.White, modifier=Modifier.padding(horizontal=20.dp)); Slider(state.effects.contrast, { vm.setEffects(contrast=it) }, valueRange=-1f..1f, modifier=Modifier.padding(horizontal=16.dp)); Text("Saturation ${(state.effects.saturation*100).roundToInt()}%", color=Color.White, modifier=Modifier.padding(horizontal=20.dp)); Slider(state.effects.saturation, { vm.setEffects(saturation=it) }, valueRange=0f..2f, modifier=Modifier.padding(horizontal=16.dp))
    Text("Luminosité ${state.effects.brightness.roundToInt()}%", color=Color.White, modifier=Modifier.padding(horizontal=20.dp)); Slider(state.effects.brightness, { vm.setEffects(brightness=it) }, valueRange=-1f..1f, modifier=Modifier.padding(horizontal=16.dp))
    Text("Teinte ${state.effects.hue.roundToInt()}°", color=Color.White, modifier=Modifier.padding(horizontal=20.dp)); Slider(state.effects.hue, { vm.setEffects(hue=it) }, valueRange=-180f..180f, modifier=Modifier.padding(horizontal=16.dp))
    Text("Flou ${state.effects.blur.roundToInt()} px", color=Color.White, modifier=Modifier.padding(horizontal=20.dp)); Slider(state.effects.blur, { vm.setEffects(blur=it) }, valueRange=0f..30f, modifier=Modifier.padding(horizontal=16.dp))
    Text("Filtres GPU", color=Color.White, style=MaterialTheme.typography.titleMedium, modifier=Modifier.padding(horizontal=20.dp, vertical=6.dp))
    LazyRow(Modifier.fillMaxWidth().padding(horizontal=16.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items(VideoFilter.entries) { filter -> FilterChip(selected=state.effects.filter==filter, onClick={vm.setEffects(filter=filter)}, label={Text(filter.name)}) }
    }
    Text("Calques / compositing", color=Color.White, style=MaterialTheme.typography.titleMedium, modifier=Modifier.padding(horizontal=20.dp, vertical=10.dp))
    var maskEnabled by remember(state.selectedClipId) { mutableStateOf(state.selectedClipId?.let { state.masks[it]?.enabled } == true) }
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp), verticalAlignment=Alignment.CenterVertically) { Text("Masque", color=Color.White, modifier=Modifier.weight(1f)); Switch(maskEnabled, { maskEnabled=it; vm.setMask((state.masks[state.selectedClipId] ?: MaskSettings()).copy(enabled=it)) }) }
    if (clip != null) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp), verticalAlignment=Alignment.CenterVertically) { Text("Mode de fusion", color=Color.White, modifier=Modifier.weight(1f)); var expanded by remember { mutableStateOf(false) }; Box { TextButton({expanded=true}) { Text((state.blendModes[clip.id] ?: BlendMode.NORMAL).name) }; DropdownMenu(expanded,{expanded=false}) { BlendMode.entries.forEach { m -> DropdownMenuItem({Text(m.name)}, onClick={vm.setBlendMode(m);expanded=false}) } } } }
        var keyEnabled by remember(clip.id) { mutableStateOf(state.chromaKeys[clip.id]?.enabled == true) }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp), verticalAlignment=Alignment.CenterVertically) { Text("Chroma key", color=Color.White, modifier=Modifier.weight(1f)); Switch(keyEnabled, {keyEnabled=it; vm.setChromaKey((state.chromaKeys[clip.id] ?: ChromaKeySettings()).copy(enabled=it))}) }
        if (keyEnabled) { val ck=state.chromaKeys[clip.id] ?: ChromaKeySettings(); Text("Seuil ${(ck.threshold*100).roundToInt()}%", color=Color.White, modifier=Modifier.padding(horizontal=20.dp)); Slider(ck.threshold,{vm.setChromaKey(ck.copy(threshold=it))},valueRange=0.01f..0.8f,modifier=Modifier.padding(horizontal=16.dp)); Text("Douceur ${(ck.softness*100).roundToInt()}%",color=Color.White,modifier=Modifier.padding(horizontal=20.dp)); Slider(ck.softness,{vm.setChromaKey(ck.copy(softness=it))},valueRange=0f..0.5f,modifier=Modifier.padding(horizontal=16.dp)) }
    }
    if (clip != null) TextButton({ vm.clearKeyframes() }, modifier=Modifier.padding(horizontal=16.dp)) { Text("Effacer les keyframes") }
    Spacer(Modifier.height(8.dp))
}
@Composable private fun SheetTitle(t:String){Text(t,color=Color.White,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp))}
@Composable private fun SheetAction(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){ListItem({Text(label,color=Color.White)},leadingContent={Icon(icon,null,tint=Accent)},modifier=Modifier.clickable(onClick=onClick),colors=ListItemDefaults.colors(containerColor=Color.Transparent))}
@Composable private fun TextDialog(close:()->Unit, add:(String)->Unit){var text by remember{mutableStateOf("")}; AlertDialog(onDismissRequest=close,title={Text("Ajouter un texte")},text={OutlinedTextField(text,{text=it},label={Text("Texte")},singleLine=true)},confirmButton={TextButton({add(text);close()}){Text("Ajouter")}},dismissButton={TextButton(close){Text("Annuler")}})}
internal fun formatTime(ms:Long):String{val t=ms.coerceAtLeast(0)/1000;return "%02d:%02d".format(t/60,t%60)}
private fun displayName(context:Context,uri:Uri):String?=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}
