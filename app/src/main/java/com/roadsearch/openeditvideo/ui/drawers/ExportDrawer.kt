package com.roadsearch.openeditvideo.ui.drawers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import com.roadsearch.openeditvideo.export.ExportResolution
import com.roadsearch.openeditvideo.export.ExportSettings
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.timelineEndMs
import com.roadsearch.openeditvideo.ui.EditorViewModel
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.theme.MfColors

private fun formatSize(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000.0) "%.1f Go".format(mb / 1000.0) else "${mb.toInt().coerceAtLeast(1)} Mo"
}

@Composable
private fun Segmented(options: List<String>, selected: Int, enabled: List<Boolean> = options.map { true }, onSelect: (Int) -> Unit) {
    val outer = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(outer).background(MfColors.Card).border(1.dp, MfColors.Outline, outer).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val inner = RoundedCornerShape(9.dp)
            Box(
                Modifier.weight(1f).height(38.dp).clip(inner)
                    .background(if (on) MfColors.Active.copy(alpha = .22f) else Color.Transparent)
                    .then(if (on) Modifier.border(1.5.dp, MfColors.Active, inner) else Modifier)
                    .pressable(enabled = enabled[i], role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = when { !enabled[i] -> MfColors.TextMuted.copy(alpha = .6f); on -> Color.White; else -> MfColors.TextSecondary },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp))
}

@Composable
private fun Caption(text: String) {
    Text(text, color = MfColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Color.White else MfColors.TextMuted, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, color = MfColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MfColors.Active, checkedThumbColor = Color.White),
        )
    }
}

/** Export dashboard: resolution, frame rate, format, quality and a live size estimate. */
@Composable
internal fun ExportDrawer(state: EditorUiState, vm: EditorViewModel, onClose: () -> Unit) {
    val settings by vm.exportSettings.collectAsStateWithLifecycle()
    val durationMs = remember(state.clips, state.audioClips, state.textOverlays) { state.timelineEndMs() }
    val first = remember(state.clips) { state.clips.filter { it.track == 0 }.minByOrNull { it.timelineStartMs } }
    val canExport = first != null
    val context = LocalContext.current
    val resolutions = ExportResolution.entries

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        DrawerHeader("Exporter", onClose)

        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2B1B4E), Color(0xFFD9653B)))),
            contentAlignment = Alignment.Center,
        ) {
            val uri = first?.uri
            if (uri != null) {
                val request = remember(uri) { ImageRequest.Builder(context).data(uri).videoFrameMillis(0L).build() }
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        SectionLabel("Résolution")
        Segmented(resolutions.map { it.label }, resolutions.indexOf(settings.resolution)) { vm.setExportSettings(settings.copy(resolution = resolutions[it])) }
        Caption("${settings.canvasSize(state.aspect).first} × ${settings.canvasSize(state.aspect).second} px (${state.aspect.label}). Les téléphones d'entrée de gamme peuvent refuser le 4K.")

        SectionLabel("Fréquence d'images")
        Segmented(ExportSettings.FPS_OPTIONS.map { "$it" }, ExportSettings.FPS_OPTIONS.indexOf(settings.fps)) {
            vm.setExportSettings(settings.copy(fps = ExportSettings.FPS_OPTIONS[it]))
        }
        Caption("Plafond : les images en trop sont retirées, jamais inventées. 60 garde la cadence d'origine.")

        SectionLabel("Format")
        Segmented(listOf("MP4", "MOV", "GIF"), 0, enabled = listOf(true, false, false)) {}
        Caption("MOV et GIF : bientôt disponibles.")

        Spacer(Modifier.height(10.dp))
        SwitchRow("HDR", "Indisponible : les effets GPU sont en SDR", checked = false, enabled = false) {}
        SwitchRow("Qualité élevée", "Débit vidéo plus élevé, fichier plus lourd", settings.highQuality, true) { vm.setExportSettings(settings.copy(highQuality = it)) }

        Spacer(Modifier.height(12.dp))
        GradientButton("Exporter", enabled = canExport) { vm.exportWith(settings); onClose() }
        Text(
            if (canExport) "Taille estimée : ≈ ${formatSize(settings.estimatedBytesFor(durationMs, state.aspect))}" else "Importez une vidéo pour exporter",
            color = MfColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
