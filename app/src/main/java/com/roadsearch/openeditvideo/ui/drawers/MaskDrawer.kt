package com.roadsearch.openeditvideo.ui.drawers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.ChromaKeySettings
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.MaskSettings
import com.roadsearch.openeditvideo.model.MaskType
import com.roadsearch.openeditvideo.ui.EditorViewModel
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.theme.MfColors

/** null = no mask. */
private class ShapeOption(val label: String, val kind: Int, val type: MaskType?)

private val Shapes = listOf(
    ShapeOption("Aucun", 0, null),
    ShapeOption("Rectangle", 1, MaskType.RECTANGLE),
    ShapeOption("Cercle", 2, MaskType.CIRCLE),
    ShapeOption("Ellipse", 3, MaskType.ELLIPSE),
    ShapeOption("Ligne", 4, MaskType.LINEAR_GRADIENT),
)

private fun MaskSettings.withShape(newType: MaskType, size: Float): MaskSettings {
    val w = size
    val h = if (newType == MaskType.ELLIPSE) size * 0.65f else size
    return copy(enabled = true, type = newType, x = (1f - w) / 2f, y = (1f - h) / 2f, width = w, height = h)
}

@Composable
private fun ShapeGlyph(kind: Int, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val stroke = Stroke(width = 2.dp.toPx())
        val w = size.width
        val h = size.height
        when (kind) {
            0 -> {
                drawCircle(color, w * 0.42f, style = stroke)
                drawLine(color, Offset(w * 0.2f, h * 0.8f), Offset(w * 0.8f, h * 0.2f), 2.dp.toPx())
            }
            1 -> drawRoundRect(color, Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, h * 0.76f), CornerRadius(3.dp.toPx()), style = stroke)
            2 -> drawCircle(color, w * 0.4f, style = stroke)
            3 -> drawOval(color, Offset(w * 0.05f, h * 0.22f), Size(w * 0.9f, h * 0.56f), style = stroke)
            else -> drawLine(color, Offset(w * 0.08f, h * 0.5f), Offset(w * 0.92f, h * 0.5f), 2.dp.toPx())
        }
    }
}

@Composable
internal fun MaskDrawer(state: EditorUiState, vm: EditorViewModel, onClose: () -> Unit) {
    val clip = state.clips.firstOrNull { it.id == state.selectedClipId }
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        DrawerHeader("Masques & Composition", onClose)
        if (clip == null) {
            EmptyNote("Sélectionnez un clip dans la timeline pour lui appliquer un masque, un mode de fusion ou une incrustation.")
            return@Column
        }
        DrawerTabs(listOf("Masque", "Fusion", "Chromakey"), tab) { tab = it }
        Spacer(Modifier.height(10.dp))
        if (tab == 1) {
            Text(
                "Les modes autres que « Normal » demandent le moteur FFmpeg optionnel pour l'export.",
                color = MfColors.TextMuted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        when (tab) {
            0 -> MaskTab(state.masks[clip.id] ?: MaskSettings(), clip.id, vm)
            1 -> {
                val current = state.blendModes[clip.id] ?: BlendMode.NORMAL
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlendMode.entries.forEach { mode ->
                        FilterChip(
                            selected = current == mode, onClick = { vm.setBlendMode(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')) },
                        )
                    }
                }
            }
            else -> ChromaTab(state.chromaKeys[clip.id] ?: ChromaKeySettings(), clip.id, vm)
        }
    }
}

@Composable
private fun MaskTab(mask: MaskSettings, clipId: Long, vm: EditorViewModel) {
    var size by remember(clipId, mask.width) { mutableFloatStateOf(mask.width.coerceIn(0.1f, 1f)) }
    var feather by remember(clipId, mask.feather) { mutableFloatStateOf(mask.feather) }
    val selectedShape = if (!mask.enabled) 0 else Shapes.indexOfFirst { it.type == mask.type }.coerceAtLeast(0)

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Shapes.forEachIndexed { i, option ->
            val on = i == selectedShape
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (on) MfColors.Active.copy(alpha = .2f) else MfColors.Card)
                        .border(if (on) 2.dp else 1.dp, if (on) MfColors.Active else MfColors.Outline, RoundedCornerShape(14.dp))
                        .pressable(role = null) {
                            val type = option.type
                            if (type == null) vm.setMask(mask.copy(enabled = false))
                            else vm.setMask(mask.withShape(type, if (mask.enabled && mask.width < 0.99f) mask.width else 0.6f))
                        },
                    contentAlignment = Alignment.Center,
                ) { ShapeGlyph(option.kind, if (on) Color.White else MfColors.TextSecondary) }
                Spacer(Modifier.height(4.dp))
                Text(option.label, color = if (on) Color.White else MfColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(MfColors.Card).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Inverser", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(
            checked = mask.invert, enabled = mask.enabled, onCheckedChange = { vm.setMask(mask.copy(invert = it)) },
            colors = SwitchDefaults.colors(checkedTrackColor = MfColors.Active, checkedThumbColor = Color.White),
        )
    }
    Spacer(Modifier.height(8.dp))
    LabeledSlider(
        "Contour", "${(feather * 100).toInt()}%", feather, 0f..0.25f, enabled = mask.enabled,
        onChange = { feather = it }, onFinished = { vm.setMask(mask.copy(feather = feather)) },
    )
    LabeledSlider(
        "Taille", "${(size * 100).toInt()}%", size, 0.1f..1f, enabled = mask.enabled,
        onChange = { size = it },
        onFinished = { mask.type.let { t -> vm.setMask(mask.withShape(t, size)) } },
    )
}

@Composable
private fun ChromaTab(key: ChromaKeySettings, clipId: Long, vm: EditorViewModel) {
    var threshold by remember(clipId, key.threshold) { mutableFloatStateOf(key.threshold) }
    var softness by remember(clipId, key.softness) { mutableFloatStateOf(key.softness) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(MfColors.Card).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Supprimer la couleur (fond vert)", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(
            checked = key.enabled, onCheckedChange = { vm.setChromaKey(key.copy(enabled = it)) },
            colors = SwitchDefaults.colors(checkedTrackColor = MfColors.Active, checkedThumbColor = Color.White),
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0xFF00FF00.toInt(), 0xFF0000FF.toInt()).forEach { argb ->
            val on = key.colorArgb == argb
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(Color(argb))
                    .border(if (on) 3.dp else 1.dp, if (on) MfColors.Active else MfColors.Outline, RoundedCornerShape(50))
                    .pressable(role = null) { vm.setChromaKey(key.copy(colorArgb = argb)) },
            )
        }
    }
    LabeledSlider(
        "Tolérance", "${(threshold * 100).toInt()}%", threshold, 0f..0.5f, enabled = key.enabled,
        onChange = { threshold = it }, onFinished = { vm.setChromaKey(key.copy(threshold = threshold)) },
    )
    LabeledSlider(
        "Adoucissement", "${(softness * 100).toInt()}%", softness, 0f..0.3f, enabled = key.enabled,
        onChange = { softness = it }, onFinished = { vm.setChromaKey(key.copy(softness = softness)) },
    )
}
