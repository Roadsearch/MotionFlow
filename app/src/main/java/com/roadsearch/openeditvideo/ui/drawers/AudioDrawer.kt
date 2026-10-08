package com.roadsearch.openeditvideo.ui.drawers

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.roadsearch.openeditvideo.ui.EditorViewModel
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.formatTime
import com.roadsearch.openeditvideo.ui.theme.MfColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class AudioCategory(val label: String, val keywords: List<String>) {
    TREND("Tendance", emptyList()),
    POP("Pop", listOf("pop")),
    LOFI("Lo-Fi", listOf("lo-fi", "lofi", "chill")),
    CINE("Ciné", listOf("cine", "ciné", "film", "soundtrack", "score", "ost")),
    ENERGY("Énergique", listOf("energ", "énerg", "dance", "edm", "rock", "workout", "hype")),
}

private class DeviceTrack(val id: Long, val uri: Uri, val title: String, val artist: String, val durationMs: Long, val tags: String)

private suspend fun queryDeviceTracks(context: Context): List<DeviceTrack> = withContext(Dispatchers.IO) {
    val columns = buildList {
        add(MediaStore.Audio.Media._ID); add(MediaStore.Audio.Media.TITLE); add(MediaStore.Audio.Media.ARTIST)
        add(MediaStore.Audio.Media.ALBUM); add(MediaStore.Audio.Media.DURATION)
        if (Build.VERSION.SDK_INT >= 30) add(MediaStore.Audio.AudioColumns.GENRE)
    }.toTypedArray()
    val out = ArrayList<DeviceTrack>()
    runCatching {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000", null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iGenre = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.Audio.AudioColumns.GENRE) else -1
            while (c.moveToNext() && out.size < 300) {
                val id = c.getLong(iId)
                val title = c.getString(iTitle) ?: "Titre inconnu"
                val artist = c.getString(iArtist)?.takeIf { it != "<unknown>" } ?: ""
                val album = c.getString(iAlbum) ?: ""
                val genre = if (iGenre >= 0) c.getString(iGenre) ?: "" else ""
                out += DeviceTrack(
                    id, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), title, artist, c.getLong(iDur),
                    "$title $artist $album $genre".lowercase(),
                )
            }
        }
    }
    out
}

private val ThumbColors = listOf(
    listOf(Color(0xFF7B2FF7), Color(0xFF00CEFD)),
    listOf(Color(0xFFB04DEB), Color(0xFFFF8A4D)),
    listOf(Color(0xFF1F6BE0), Color(0xFF19C9E0)),
    listOf(Color(0xFF3B3B4F), Color(0xFFC546FF)),
)

@Composable
internal fun AudioDrawer(vm: EditorViewModel, onPickFile: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var tracks by remember { mutableStateOf<List<DeviceTrack>?>(null) }
    LaunchedEffect(granted) { if (granted) tracks = queryDeviceTracks(context) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(AudioCategory.TREND) }
    var section by remember { mutableStateOf(0) }
    var added by remember { mutableStateOf(setOf<Long>()) }

    val shown = remember(tracks, query, category) {
        val q = query.trim().lowercase()
        tracks.orEmpty().filter { t ->
            (category.keywords.isEmpty() || category.keywords.any { it in t.tags }) && (q.isEmpty() || q in t.tags)
        }
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.82f)) {
        DrawerHeader("Audio", onClose)
        DrawerTabs(listOf("Musique", "Effets sonores", "Voix IA"), section) { section = it }
        if (section != 0) {
            EmptyNote("Cette section sera bientôt disponible.")
        } else {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Rechercher une musique", color = MfColors.TextMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MfColors.TextMuted) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedContainerColor = MfColors.Card, unfocusedContainerColor = MfColors.Card,
                    focusedBorderColor = MfColors.Active, unfocusedBorderColor = MfColors.Outline, cursorColor = MfColors.Active,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AudioCategory.entries.forEach { c ->
                    val on = c == category
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (on) MfColors.Active.copy(alpha = .22f) else MfColors.Card)
                            .border(1.dp, if (on) MfColors.Active else MfColors.Outline, RoundedCornerShape(50))
                            .pressable(role = null) { category = c }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) { Text(c.label, color = if (on) Color.White else MfColors.TextSecondary, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !granted -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, tint = MfColors.Active, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Autorisez l'accès à votre musique pour la parcourir ici.", color = MfColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        GradientButton("Autoriser l'accès") { request.launch(permission) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Importer un fichier audio", color = MfColors.Active, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable(onClick = onPickFile).padding(12.dp),
                        )
                    }
                    tracks == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MfColors.Active) }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "file") {
                            Row(
                                Modifier.fillMaxWidth().clickable(onClick = onPickFile).padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.FileUpload, null, tint = MfColors.Active)
                                Spacer(Modifier.width(12.dp))
                                Text("Importer un fichier audio", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        if (shown.isEmpty()) {
                            item(key = "empty") {
                                EmptyNote(if (tracks.orEmpty().isEmpty()) "Aucune musique trouvée sur cet appareil." else "Aucun titre ne correspond à ce filtre.")
                            }
                        }
                        items(shown, key = { it.id }) { t ->
                            val isAdded = t.id in added
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                                        .background(Brush.linearGradient(ThumbColors[abs(t.id.hashCode()) % ThumbColors.size])),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = .85f)) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(formatTime(t.durationMs), t.artist).filter { it.isNotBlank() }.joinToString(" · "),
                                        color = MfColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(if (isAdded) Brush.linearGradient(listOf(Color(0xFF1FB58F), Color(0xFF168F70))) else MfColors.brandBrush())
                                        .pressable(enabled = !isAdded) { vm.import(t.uri, t.title, true); added = added + t.id },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(if (isAdded) Icons.Rounded.Check else Icons.Rounded.Add, if (isAdded) "Ajouté" else "Ajouter", tint = Color.White) }
                            }
                        }
                    }
                }
            }
        }
    }
}
