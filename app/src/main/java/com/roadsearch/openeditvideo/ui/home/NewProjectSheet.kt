package com.roadsearch.openeditvideo.ui.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadsearch.openeditvideo.media.ProjectBackgrounds
import com.roadsearch.openeditvideo.model.AspectRatio
import com.roadsearch.openeditvideo.ui.drawers.GradientButton
import com.roadsearch.openeditvideo.ui.theme.MfColors

/**
 * Mandatory first step of a new project: pick a photo/video from the device, or start from a background
 * (solid colour / gradient). Nothing is created and the editor is not opened until one of the two is chosen;
 * closing the sheet simply cancels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectSheet(
    onDismiss: () -> Unit,
    onMedia: (Uri, String) -> Unit,
    onBackground: (ProjectBackgrounds.Preset, AspectRatio) -> Unit,
) {
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var aspectName by rememberSaveable { mutableStateOf(AspectRatio.PORTRAIT.name) }
    val aspect = AspectRatio.valueOf(aspectName)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onMedia(it, displayNameOf(context, it)) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MfColors.Surface,
        contentColor = Color.White,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Nouveau projet", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fermer", tint = Color.White) }
            }
            Text(
                "Pour commencer, ajoutez une photo, une vidéo ou choisissez un fond.",
                color = MfColors.TextSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp),
            )
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))
                        .background(MfColors.brandBrush())
                        .clickable { picker.launch(arrayOf("video/*", "image/*")) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("Photo ou vidéo", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Depuis votre galerie ou vos fichiers", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    }
                }

                Text("ou partez d'un fond", color = MfColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp))
                ProjectBackgrounds.presets.chunked(4).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { preset ->
                            BackgroundSwatch(preset, preset.id == selectedId) { selectedId = preset.id }
                        }
                        repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Text("Format", color = MfColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 8.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AspectRatio.entries.forEach { option ->
                        val on = option == aspect
                        val shape = RoundedCornerShape(50)
                        Box(
                            Modifier.clip(shape)
                                .background(if (on) MfColors.Active.copy(alpha = 0.22f) else MfColors.Card)
                                .border(1.dp, if (on) MfColors.Active else MfColors.Outline, shape)
                                .clickable { aspectName = option.name }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text(option.label, color = if (on) Color.White else MfColors.TextSecondary, fontSize = 13.sp) }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            val chosen = ProjectBackgrounds.presets.firstOrNull { it.id == selectedId }
            GradientButton(
                text = if (chosen == null) "Choisissez un fond ou un média" else "Créer avec le fond « ${chosen.label} »",
                enabled = chosen != null,
            ) { chosen?.let { onBackground(it, aspect) } }
        }
    }
}

@Composable
private fun RowScope.BackgroundSwatch(preset: ProjectBackgrounds.Preset, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val colors = preset.colors.map { Color(it) }
    val brush: Brush = if (preset.isGradient) Brush.verticalGradient(colors) else SolidColor(colors.first())
    val checkTint = if (colors.first().luminance() > 0.6f) Color.Black else Color.White
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(shape).background(brush)
                .border(if (selected) 2.dp else 1.dp, if (selected) MfColors.Active else MfColors.Outline, shape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Fond ${preset.label}" },
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Rounded.Check, null, tint = checkTint) }
        Spacer(Modifier.height(4.dp))
        Text(preset.label, color = if (selected) Color.White else MfColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

private fun displayNameOf(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: "Média"
