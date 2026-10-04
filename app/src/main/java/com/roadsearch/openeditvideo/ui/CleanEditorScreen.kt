package com.roadsearch.openeditvideo.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.selectedClip

private val CleanBg = Color(0xFF080B10)
private val CleanPanel = Color(0xFF11161E)
private val CleanCard = Color(0xFF1A202A)
private val CleanMuted = Color(0xFF9AA4B2)
private val CleanAccent = Color(0xFF20D7C4)

private enum class PreviewRatio(val label: String, val value: Float) {
    PORTRAIT("9:16", 9f / 16f),
    LANDSCAPE("16:9", 16f / 9f)
}

@Composable
fun CleanEditorScreen(vm: EditorViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var ratio by remember { mutableStateOf(PreviewRatio.PORTRAIT) }
    var menu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.import(it, displayName(context, it) ?: "Video") }
    }
    val open = { picker.launch(arrayOf("video/*", "image/*")) }

    Surface(color = CleanBg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CleanTopBar(state, { menu = true }, open, vm::exportSelected)
            PreviewStage(state, ratio, context, open)
            Controls(state, ratio, { ratio = it }, { vm.seekTo((state.positionMs - 5000).coerceAtLeast(0)) },
                { vm.setPlaying(!state.playing) },
                { vm.seekTo((state.positionMs + 5000).coerceAtMost(state.durationMs)) })
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp)) {
                Text("Timeline", color = Color.White, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(state.clips.size.toString() + " média(s)", color = CleanMuted,
                    style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.height(150.dp)) { InteractiveTimeline(state, vm) }
            ToolDock(expanded, { expanded = !expanded }, open, vm::split, vm::deleteSelected,
                { vm.addText("Nouveau texte") }, vm::toggleMute, { menu = true })
        }
    }

    if (menu) {
        ModalBottomSheet(onDismissRequest = { menu = false }, containerColor = CleanPanel) {
            Text("Actions du clip", color = Color.White, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(20.dp))
            ActionRow("Rogner le début", Icons.Rounded.FirstPage) { vm.trimStart(); menu = false }
            ActionRow("Rogner la fin", Icons.Rounded.LastPage) { vm.trimEnd(); menu = false }
            ActionRow("Dupliquer", Icons.Rounded.ContentCopy) { vm.duplicateSelected(); menu = false }
            ActionRow("Ajouter une transition", Icons.Rounded.AutoAwesomeMotion) {
                vm.addTransition(); menu = false
            }
            ActionRow("Effets / keyframes", Icons.Rounded.AutoFixHigh) {
                vm.setEffects(contrast = 0.05f); menu = false
            }
            ActionRow("Fermer", Icons.Rounded.Close) { menu = false }
            Spacer(Modifier.navigationBarsPadding().height(18.dp))
        }
    }
}

@Composable
private fun CleanTopBar(state: EditorUiState, menu: () -> Unit, open: () -> Unit, export: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton({}) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text("Nouveau projet", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("1080p · 30 fps", color = CleanMuted, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(open) { Icon(Icons.Rounded.Add, "Importer", tint = Color.White) }
        IconButton(menu) { Icon(Icons.Rounded.MoreVert, "Plus", tint = Color.White) }
        FilledTonalButton(export, enabled = state.selectedClipId != null,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = CleanAccent, contentColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 13.dp)) {
            Icon(Icons.Rounded.FileUpload, null, Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
            Text("Exporter")
        }
    }
}

@Composable
private fun PreviewStage(state: EditorUiState, ratio: PreviewRatio, context: Context, open: () -> Unit) {
    val clip = state.selectedClip()
    Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        if (clip == null) {
            Box(Modifier.fillMaxWidth(.72f).aspectRatio(ratio.value).clip(RoundedCornerShape(18.dp))
                .background(CleanCard).clickable(onClick = open), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, tint = CleanAccent, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Importer une vidéo", color = Color.White)
                    Text("Aperçu " + ratio.label, color = CleanMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            val player = remember(clip.id) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(clip.uri)); prepare()
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            LaunchedEffect(state.playing) { if (state.playing) player.play() else player.pause() }
            LaunchedEffect(state.seekNonce, clip.id) {
                player.seekTo((state.positionMs - clip.timelineStartMs + clip.startMs).coerceAtLeast(clip.startMs))
            }
            AndroidView({ PlayerView(it).apply { player = player; useController = false } },
                modifier = Modifier.fillMaxWidth(.72f).aspectRatio(ratio.value)
                    .clip(RoundedCornerShape(18.dp)).background(Color.Black))
        }
    }
}

@Composable
private fun Controls(state: EditorUiState, ratio: PreviewRatio, setRatio: (PreviewRatio) -> Unit,
    back: () -> Unit, play: () -> Unit, forward: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(state.positionMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            PreviewRatio.entries.forEach {
                FilterChip(ratio == it, { setRatio(it) }, label = { Text(it.label) },
                    modifier = Modifier.padding(start = 4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.Rounded.Replay5, null, tint = Color.White) }
            FilledIconButton(play, shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = CleanAccent)) {
                Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black)
            }
            IconButton(forward) { Icon(Icons.Rounded.Forward5, null, tint = Color.White) }
            Spacer(Modifier.width(10.dp))
            Text(formatTime(state.durationMs), color = CleanMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ToolDock(expanded: Boolean, toggle: () -> Unit, open: () -> Unit, cut: () -> Unit,
    delete: () -> Unit, text: () -> Unit, mute: () -> Unit, more: () -> Unit) {
    Surface(color = CleanPanel, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (expanded) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolButton("Média", Icons.Rounded.VideoLibrary, open)
                ToolButton("Couper", Icons.Rounded.ContentCut, cut)
                ToolButton("Texte", Icons.Rounded.TextFields, text)
                ToolButton("Son", Icons.Rounded.VolumeUp, mute)
                ToolButton("Supprimer", Icons.Rounded.DeleteOutline, delete)
                ToolButton("Plus", Icons.Rounded.MoreHoriz, more)
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(toggle, shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (expanded) CleanAccent else CleanCard)) {
                    Icon(if (expanded) Icons.Rounded.Close else Icons.Rounded.Add,
                        "Afficher les outils", tint = if (expanded) Color.Black else Color.White)
                }
                if (!expanded) {
                    Spacer(Modifier.width(10.dp))
                    Text("Appuyer sur + pour afficher les outils", color = CleanMuted,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(54.dp)) {
        IconButton(action) { Icon(icon, label, tint = Color.White) }
        Text(label, color = CleanMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ActionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    ListItem(headlineContent = { Text(label, color = Color.White) },
        leadingContent = { Icon(icon, null, tint = CleanAccent) },
        modifier = Modifier.clickable(onClick = action),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%02d:%02d".format(total / 60, total % 60)
}
