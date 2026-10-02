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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private val Bg = Color(0xFF08090D)
private val Panel = Color(0xFF101219)
private val Card = Color(0xFF181B24)
private val Muted = Color(0xFF8C92A3)
private val Accent = Color(0xFF8B7CFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel) {
    val state by vm.state.collectAsState(); val context = LocalContext.current
    var more by remember { mutableStateOf(false) }; var sheet by remember { mutableStateOf<Tool?>(null) }; var textDialog by remember { mutableStateOf(false) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.import(u, displayName(context, u) ?: "Video") } }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.import(u, displayName(context, u) ?: "Audio", true) } }
    fun pickVideo() { videoPicker.launch(arrayOf("video/*", "image/*")) }
    fun pickAudio() { audioPicker.launch(arrayOf("audio/*")) }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()) {
        TopBar({ pickVideo() }, vm::exportSelected, more, { more = it }, state.selectedClipId != null, vm)
        Preview(context, state, vm)
        Timeline(state, vm)
        if (state.exportProgress != null || state.exportMessage != null) {
            ExportBanner(state.exportProgress, state.exportMessage, vm)
        }
        Toolbar(state, vm, { pickVideo() }, { sheet = Tool.MORE })
    }}

    sheet?.let { tool ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Panel) {
            when (tool) {
                Tool.AUDIO -> { SheetTitle("Audio"); SheetAction("Ajouter une piste audio", Icons.Rounded.MusicNote) { pickAudio(); sheet = null }; SheetAction("Couper le son de la vidéo", Icons.Rounded.VolumeOff) { vm.toggleMute(); sheet = null } }
                Tool.TEXT -> { SheetTitle("Texte"); SheetAction("Ajouter un texte à la tête de lecture", Icons.Rounded.TextFields) { textDialog = true; sheet = null } }
                Tool.EFFECTS, Tool.ADJUST -> EffectsPanel(state, vm)
                Tool.MORE -> AnimationPanel(vm)
                else -> { SheetTitle("Actions du clip"); SheetAction("Scinder ici", Icons.Rounded.ContentCut) { vm.split(); sheet = null }; SheetAction("Rogner le début", Icons.Rounded.FirstPage) { vm.trimStart(); sheet = null }; SheetAction("Rogner la fin", Icons.Rounded.LastPage) { vm.trimEnd(); sheet = null }; SheetAction("Supprimer", Icons.Rounded.DeleteOutline) { vm.deleteSelected(); sheet = null } }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
    if (textDialog) TextDialog({ textDialog = false }, vm::addText)
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
                    progress = { (progress ?: 0f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable private fun TopBar(onImport: () -> Unit, onExport: () -> Unit, more: Boolean, setMore: (Boolean) -> Unit, canExport: Boolean, vm: EditorViewModel) {
    Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({}) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White) }
        Column(Modifier.weight(1f)) { Text("Nouveau projet", color = Color.White, style = MaterialTheme.typography.titleMedium); Text("1080p · 30 fps", color = Muted, style = MaterialTheme.typography.labelSmall) }
        IconButton({ vm.undo() }, enabled = vm.canUndo()) { Icon(Icons.Rounded.Undo, "Annuler", tint = if (vm.canUndo()) Color.White else Muted) }
        IconButton({ vm.redo() }, enabled = vm.canRedo()) { Icon(Icons.Rounded.Redo, "Rétablir", tint = if (vm.canRedo()) Color.White else Muted) }
        IconButton(onImport) { Icon(Icons.Rounded.Add, "Importer", tint = Color.White) }
        Box { IconButton({ setMore(true) }) { Icon(Icons.Rounded.MoreVert, "Plus", tint = Color.White) }; DropdownMenu(more, { setMore(false) }) { DropdownMenuItem({ Text("Importer un média") }, leadingIcon = { Icon(Icons.Rounded.VideoLibrary, null) }, onClick = { setMore(false); onImport() }); DropdownMenuItem({ Text("Exporter") }, leadingIcon = { Icon(Icons.Rounded.FileUpload, null) }, enabled = canExport, onClick = { setMore(false); onExport() }) } }
        FilledTonalButton(onExport, enabled = canExport, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Accent, contentColor = Color.White), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.FileUpload, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Exporter") }
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun ColumnScope.Preview(context: Context, state: EditorUiState, vm: EditorViewModel) {
    val clip = state.selectedClip()
    Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        if (clip == null) EmptyPreview() else {
            val player = remember(clip.id) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs).apply { if (clip.endMs > 0) setEndPositionMs(clip.endMs) }.build()).build()); prepare() } }
            DisposableEffect(player) { onDispose { player.release() } }
            LaunchedEffect(state.playing) { if (state.playing) player.play() else player.pause() }
            LaunchedEffect(state.seekNonce, clip.id) {
                val localPosition = (state.positionMs - clip.timelineStartMs + clip.startMs)
                    .coerceIn(clip.startMs, clip.end(state.durationMs))
                if (kotlin.math.abs(player.currentPosition - localPosition) > 80L) player.seekTo(localPosition)
            }
            LaunchedEffect(state.muted, clip.id, clip.effects, clip.animation, state.chromaKeys[clip.id]) {
                player.volume = if (state.muted) 0f else clip.volume.coerceIn(0f, 2f)
                player.setVideoEffects(vm.previewEffects(clip))
            }
            LaunchedEffect(player, clip.id) {
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    val local = player.currentPosition
                    val timeline = (clip.timelineStartMs + (local - clip.startMs)).coerceAtLeast(0L)
                    vm.setPosition(timeline)
                    if (player.duration > 0) vm.setDuration(maxOf(state.durationMs, timeline + (player.duration - local).coerceAtLeast(0L)))
                    delay(100)
                }
            }
            DisposableEffect(player) { val l = object : Player.Listener { override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_ENDED) vm.setPlaying(false); if (player.duration > 0) vm.setDuration(player.duration) } }; player.addListener(l); onDispose { player.removeListener(l) } }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val localTime = (state.positionMs - clip.timelineStartMs + clip.startMs).coerceAtLeast(clip.startMs)
                val keyframe = clip.keyframesAt(state.positionMs)
                AndroidView({ PlayerView(it).apply { this.player = player; useController = false } }, modifier = Modifier.aspectRatio(9f / 16f).fillMaxHeight().background(Color.Black, RoundedCornerShape(18.dp)))
                Row(verticalAlignment = Alignment.CenterVertically) { IconButton({ vm.setPlaying(!state.playing) }) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White) }; Text(formatTime(state.positionMs), color = Color.White); Text(" / ${formatTime(state.durationMs)}", color = Muted); IconButton(vm::toggleMute) { Icon(if (state.muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, null, tint = Color.White) } }
            }
        }
    }
}

@Composable private fun EmptyPreview() { Box(Modifier.aspectRatio(9f / 16f).fillMaxHeight().background(Card, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.VideoLibrary, null, tint = Muted, modifier = Modifier.size(44.dp)); Spacer(Modifier.height(8.dp)); Text("Importe une vidéo", color = Muted) } } }

@Composable private fun Timeline(state: EditorUiState, vm: EditorViewModel) { InteractiveTimeline(state, vm) }

@Composable private fun Toolbar(state: EditorUiState, vm: EditorViewModel, add: () -> Unit, more: () -> Unit) { Row(Modifier.fillMaxWidth().height(74.dp).background(Panel).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { listOf(Tool.MEDIA,Tool.AUDIO,Tool.TEXT,Tool.EFFECTS,Tool.ADJUST).forEach { t -> ToolButton(t, state.activeTool == t) { vm.tool(t); more() } }; ToolButton(Tool.MORE, false, more); Spacer(Modifier.width(4.dp)); FloatingActionButton(add, containerColor = Accent, contentColor = Color.White, modifier = Modifier.size(50.dp)) { Icon(Icons.Rounded.Add, null) } } }
@Composable private fun ToolButton(tool: Tool, active: Boolean, onClick: () -> Unit) { val icon = when(tool){Tool.MEDIA->Icons.Rounded.VideoLibrary;Tool.AUDIO->Icons.Rounded.MusicNote;Tool.TEXT->Icons.Rounded.TextFields;Tool.EFFECTS->Icons.Rounded.AutoAwesome;Tool.ADJUST->Icons.Rounded.Tune;Tool.MORE->Icons.Rounded.MoreVert}; Column(Modifier.width(70.dp).clickable(onClick = onClick).padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally){Icon(icon,null,tint=if(active)Accent else Color.LightGray);Text(tool.label,color=if(active)Accent else Color.LightGray,style=MaterialTheme.typography.labelSmall)} }

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
    Divider(Modifier.padding(vertical=8.dp), color=Card)
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
