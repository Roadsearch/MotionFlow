package com.roadsearch.openeditvideo.ui.drawers

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.theme.MfColors

private class AiTool(val title: String, val subtitle: String, val icon: ImageVector)

private val AiTools = listOf(
    AiTool("Génération de sous-titres", "Auto • Multilingue • Style", Icons.Rounded.Subtitles),
    AiTool("Effet de fond intelligent", "Supprimer / Remplacer / Flouter", Icons.Rounded.AutoFixHigh),
    AiTool("Génération d'images", "Texte → Image • IA", Icons.Rounded.Image),
    AiTool("Voix IA", "Clonage • Doublage • Narration", Icons.Rounded.RecordVoiceOver),
    AiTool("Amélioration vidéo", "Upscale • Denoise • Stabilisation", Icons.Rounded.HighQuality),
    AiTool("Montage automatique", "Créer une vidéo avec vos clips", Icons.Rounded.MovieCreation),
)

/** UI only: none of these actions exist in the engine yet, so each row says so instead of faking a result. */
@Composable
internal fun AiToolsDrawer(onClose: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        DrawerHeader("Outils IA", onClose)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AiTools.forEach { tool ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MfColors.Card)
                        .border(1.dp, MfColors.Outline, RoundedCornerShape(16.dp))
                        .pressable(role = null, pressedScale = 0.98f) {
                            Toast.makeText(context, "${tool.title} : bientôt disponible", Toast.LENGTH_SHORT).show()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MfColors.CardHigh), contentAlignment = Alignment.Center) {
                        Icon(tool.icon, null, tint = MfColors.Active)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tool.title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                        Text(tool.subtitle, color = MfColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Bientôt", color = MfColors.TextMuted, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(MfColors.CardHigh).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
