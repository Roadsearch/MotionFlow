package com.roadsearch.openeditvideo.ui.drawers

import androidx.compose.foundation.background
import com.roadsearch.openeditvideo.media.TextShadow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TextPreset
import com.roadsearch.openeditvideo.model.TextStyleSpec
import com.roadsearch.openeditvideo.ui.EditorViewModel
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.theme.BebasNeue
import com.roadsearch.openeditvideo.ui.theme.Inter
import com.roadsearch.openeditvideo.ui.theme.MfColors

internal class PresetUi(val preset: TextPreset, val label: String, val font: String, val color: Int)

internal val Presets = listOf(
    PresetUi(TextPreset.CLASSIC, "Classique", "sans", 0xFFFFFFFF.toInt()),
    PresetUi(TextPreset.NEON, "Néon", "bebas", 0xFFFF4FD8.toInt()),
    PresetUi(TextPreset.SCRIPT, "Manuscrit", "cursive", 0xFFFFFFFF.toInt()),
    PresetUi(TextPreset.BOLD3D, "3D", "bebas", 0xFF19E0F5.toInt()),
)

internal val Fonts = listOf("bebas" to "Bebas Neue", "inter" to "Inter", "sans" to "Sans-serif", "serif" to "Serif", "cursive" to "Cursive")

internal val Palette = listOf(
    0xFFFFFFFF, 0xFFFF80C8, 0xFFFF4FD8, 0xFFB04DEB, 0xFF4F6BFF, 0xFF19E0F5, 0xFF3DDC84, 0xFFFFD54F,
).map { it.toInt() }

internal fun familyFor(font: String): FontFamily = when (font) {
    "bebas" -> BebasNeue
    "inter" -> Inter
    "serif" -> FontFamily.Serif
    "cursive" -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}

/** Compose approximation of the exported text (the neon glow only exists in this preview). */
internal fun previewStyle(style: TextStyleSpec, sizeSp: Float): TextStyle = TextStyle(
    fontFamily = familyFor(style.font),
    color = Color(style.colorArgb),
    fontSize = sizeSp.sp,
    fontWeight = if (style.preset == TextPreset.CLASSIC || style.preset == TextPreset.BOLD3D) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if (style.preset == TextPreset.SCRIPT) FontStyle.Italic else FontStyle.Normal,
    shadow = when (style.preset) {
        TextPreset.NEON -> Shadow(Color(style.colorArgb), Offset.Zero, 24f)
        TextPreset.BOLD3D -> Shadow(Color(0xFF6A4DEB), Offset(4f, 4f), 0f)
        else -> Shadow(Color(TextShadow.COLOR), Offset(0f, TextShadow.offsetY(sizeSp)), TextShadow.radius(sizeSp))
    },
)

@Composable
internal fun TextDrawer(state: EditorUiState, vm: EditorViewModel, editing: Boolean, onClose: () -> Unit) {
    val existing = if (editing) state.textOverlays.firstOrNull { it.id == state.selectedTextId } else null
    var text by remember { mutableStateOf(existing?.text ?: "Votre texte") }
    var style by remember { mutableStateOf(existing?.style ?: TextStyleSpec()) }
    var tab by remember { mutableStateOf(0) }
    var fontMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        DrawerHeader(if (existing != null) "Style du texte" else "Texte & Overlays", onClose)

        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2B1B4E), Color(0xFFD9653B)))).padding(12.dp),
            contentAlignment = BiasAlignment(0f, -style.posY.coerceIn(-0.85f, 0.85f)),
        ) {
            Text(
                text.ifBlank { " " }, style = previewStyle(style, (style.size / 3f).coerceIn(14f, 52f)),
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            label = { Text("Texte") }, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedContainerColor = MfColors.Card, unfocusedContainerColor = MfColors.Card,
                focusedBorderColor = MfColors.Active, unfocusedBorderColor = MfColors.Outline, cursorColor = MfColors.Active,
                focusedLabelColor = MfColors.Active, unfocusedLabelColor = MfColors.TextSecondary,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(6.dp))
        DrawerTabs(listOf("Style", "Position"), tab) { tab = it }
        Spacer(Modifier.height(8.dp))

        if (tab == 0) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Presets.forEach { p ->
                    val on = style.preset == p.preset
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(MfColors.Card)
                                .border(if (on) 2.dp else 1.dp, if (on) MfColors.Violet else MfColors.Outline, RoundedCornerShape(14.dp))
                                .pressable(role = null) { style = style.copy(preset = p.preset, font = p.font, colorArgb = p.color) },
                            contentAlignment = Alignment.Center,
                        ) { Text("Aa", style = previewStyle(TextStyleSpec(p.preset, p.font, p.color), 26f)) }
                        Spacer(Modifier.height(4.dp))
                        Text(p.label, color = if (on) Color.White else MfColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Police", color = MfColors.TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                Box(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MfColors.Card)
                            .border(1.dp, MfColors.Outline, RoundedCornerShape(12.dp))
                            .clickable { fontMenu = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(Fonts.firstOrNull { it.first == style.font }?.second ?: "Sans-serif", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ArrowDropDown, null, tint = MfColors.TextSecondary)
                    }
                    DropdownMenu(fontMenu, { fontMenu = false }, containerColor = MfColors.CardHigh) {
                        Fonts.forEach { (key, label) ->
                            DropdownMenuItem({ Text(label, color = Color.White) }, onClick = { style = style.copy(font = key); fontMenu = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Couleur", color = MfColors.TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palette.forEach { argb ->
                        val on = style.colorArgb == argb
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Color(argb))
                                .border(if (on) 3.dp else 1.dp, if (on) MfColors.Active else MfColors.Outline, CircleShape)
                                .pressable(role = null) { style = style.copy(colorArgb = argb) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LabeledSlider("Taille", "${style.size.toInt()}", style.size, 28f..140f, onChange = { style = style.copy(size = it) })
        } else {
            LabeledSlider(
                "Position verticale", if (style.posY > 0.05f) "Haut" else if (style.posY < -0.05f) "Bas" else "Centre",
                style.posY, -0.9f..0.9f, onChange = { style = style.copy(posY = it) },
            )
            LabeledSlider("Taille", "${style.size.toInt()}", style.size, 28f..140f, onChange = { style = style.copy(size = it) })
        }
        Spacer(Modifier.height(8.dp))
        GradientButton(if (existing != null) "Appliquer" else "Ajouter à la timeline", enabled = text.isNotBlank()) {
            if (existing != null) vm.updateText(existing.id, text, style) else vm.addText(text, style)
            onClose()
        }
    }
}
