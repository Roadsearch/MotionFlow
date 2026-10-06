package com.roadsearch.openeditvideo.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.formatTime
import com.roadsearch.openeditvideo.ui.theme.MfColors
import com.roadsearch.openeditvideo.ui.theme.sc
import kotlin.math.abs

private val CrownIcon: ImageVector = ImageVector.Builder("Crown", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
        moveTo(3f, 19f); horizontalLineToRelative(18f); verticalLineToRelative(2f); horizontalLineTo(3f); close()
        moveTo(3f, 7f); lineToRelative(4.5f, 4f); lineTo(12f, 4f); lineToRelative(4.5f, 7f); lineTo(21f, 7f); lineToRelative(-2f, 10f); horizontalLineTo(5f); close()
    }
}.build()

@Composable
fun ProBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MfColors.Card)
            .border(BorderStroke(1.dp, MfColors.Gold.copy(alpha = .55f)), RoundedCornerShape(50))
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp.sc(), vertical = 6.dp.sc()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CrownIcon, contentDescription = null, tint = MfColors.Gold, modifier = Modifier.size(16.dp.sc()))
        Spacer(Modifier.width(6.dp))
        Text("Pro", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun NewProjectButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp.sc())
    val brush = remember { Brush.linearGradient(MfColors.BrandGradient) }
    Box(
        modifier
            .fillMaxWidth()
            .height(92.dp.sc())
            .shadow(14.dp, shape, ambientColor = MfColors.Cyan, spotColor = MfColors.Violet)
            .clip(shape)
            .background(brush)
            .pressable(pressedScale = 0.98f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp.sc()))
                Spacer(Modifier.width(6.dp))
                Text("Nouveau projet", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Text("Commencer à éditer", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

enum class QuickTool(val label: String, val icon: ImageVector) {
    CAMERA("Caméra", Icons.Rounded.PhotoCamera),
    AI("IA", Icons.Rounded.AutoAwesome),
    TEMPLATES("Modèles", Icons.Rounded.Dashboard),
    IMPORT("Importer", Icons.Rounded.FileUpload),
}

/** 4-column grid; weights + aspectRatio keep tiles square at every screen width. */
@Composable
fun QuickToolsRow(onTool: (QuickTool) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp.sc())) {
        QuickTool.entries.forEach { tool ->
            Column(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp.sc()))
                    .background(MfColors.Card)
                    .border(1.dp, MfColors.Outline, RoundedCornerShape(18.dp.sc()))
                    .pressable { onTool(tool) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(tool.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp.sc()))
                Spacer(Modifier.height(8.dp.sc()))
                Text(tool.label, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) {
            Row(Modifier.pressable(role = null, pressedScale = 0.94f, onClick = onAction).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(action, color = MfColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MfColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private val ThumbPalettes = listOf(
    listOf(Color(0xFF3A2A6B), Color(0xFFE08A3C)),
    listOf(Color(0xFF6B1F7A), Color(0xFF1F6BE0)),
    listOf(Color(0xFF0F3B6B), Color(0xFF19C9E0)),
    listOf(Color(0xFF3B3B4F), Color(0xFFC546FF)),
    listOf(Color(0xFF1C5B4A), Color(0xFFE0C23C)),
)

@Composable
fun ProjectThumbnail(project: RecentProject, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val brush = remember(project.id) {
        Brush.linearGradient(ThumbPalettes[abs(project.id.hashCode()) % ThumbPalettes.size])
    }
    Box(modifier.clip(RoundedCornerShape(14.dp.sc())).background(brush), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.VideoLibrary, null, tint = Color.White.copy(alpha = .55f), modifier = Modifier.size(22.dp))
        val uri = project.thumbnailUri
        if (uri != null) {
            val request = remember(uri, project.thumbnailMs) { ImageRequest.Builder(context).data(uri).videoFrameMillis(project.thumbnailMs).build() }
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun RecentProjectRow(
    project: RecentProject,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Row(
        modifier.fillMaxWidth().pressable(role = null, pressedScale = 0.985f, onClick = onOpen).padding(vertical = 7.dp.sc()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProjectThumbnail(project, Modifier.size(width = 60.dp.sc(), height = 76.dp.sc()))
        Spacer(Modifier.width(14.dp.sc()))
        Column(Modifier.weight(1f)) {
            Text(project.name, color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text("${formatTime(project.durationMs)} · ${project.resolutionLabel}", color = MfColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(relativeTimeFr(project.updatedAt), color = MfColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Plus d'actions", tint = MfColors.TextSecondary) }
            DropdownMenu(menu, { menu = false }, containerColor = MfColors.CardHigh) {
                DropdownMenuItem({ Text("Renommer", color = Color.White) }, onClick = { menu = false; renaming = true })
                DropdownMenuItem({ Text("Dupliquer", color = Color.White) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem({ Text("Supprimer", color = MfColors.Danger) }, onClick = { menu = false; deleting = true })
            }
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = MfColors.CardHigh,
            title = { Text("Renommer le projet", color = Color.White) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton({ onRename(text.trim()); renaming = false }) { Text("Valider") } },
            dismissButton = { TextButton({ renaming = false }) { Text("Annuler") } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = MfColors.CardHigh,
            title = { Text("Supprimer « ${project.name} » ?", color = Color.White) },
            text = { Text("Cette action est définitive. Vos fichiers vidéo d'origine ne sont pas supprimés.", color = MfColors.TextSecondary) },
            confirmButton = { TextButton({ onDelete(); deleting = false }) { Text("Supprimer", color = MfColors.Danger) } },
            dismissButton = { TextButton({ deleting = false }) { Text("Annuler") } },
        )
    }
}

@Composable
fun EmptyProjects(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp.sc()), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp.sc()).clip(CircleShape).background(MfColors.Card).border(1.dp, MfColors.Outline, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.VideoLibrary, null, tint = MfColors.Cyan, modifier = Modifier.size(28.dp.sc()))
        }
        Spacer(Modifier.height(14.dp))
        Text("Aucun projet pour l'instant", color = Color.White, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text("Créez un projet ou importez une vidéo pour commencer.", color = MfColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

/** « Il y a 2 h », « Il y a 1 j »… */
fun relativeTimeFr(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val min = ((now - timestamp) / 60_000L).coerceAtLeast(0)
    return when {
        min < 1 -> "À l'instant"
        min < 60 -> "Il y a $min min"
        min < 60 * 24 -> "Il y a ${min / 60} h"
        min < 60 * 24 * 30 -> "Il y a ${min / (60 * 24)} j"
        else -> "Il y a ${min / (60 * 24 * 30)} mois"
    }
}
