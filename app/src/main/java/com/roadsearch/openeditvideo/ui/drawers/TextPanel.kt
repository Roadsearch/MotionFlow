package com.roadsearch.openeditvideo.ui.drawers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TextStyleSpec
import com.roadsearch.openeditvideo.model.NullObject
import com.roadsearch.openeditvideo.model.Keyframe
import com.roadsearch.openeditvideo.model.at
import com.roadsearch.openeditvideo.scene.SceneGraph
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.theme.MfColors

/** Text being edited. Lives above the timeline (not in the model) until the user confirms with the check mark. */
internal data class TextDraft(val text: String, val style: TextStyleSpec, val targetId: Long?, val parentId: Long? = null)

/** Non-modal bottom panel: no scrim, the top bar, the preview and the transport stay visible and live. */
@Composable
internal fun BoxScope.TextPanelHost(draft: TextDraft?, nullObjects: List<NullObject>, onChange: (TextDraft) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    var last by remember { mutableStateOf(draft) }
    if (draft != null) last = draft
    AnimatedVisibility(
        visible = draft != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) { last?.let { TextPanel(it, nullObjects, onChange, onConfirm, onCancel) } }
}

@Composable
private fun TextPanel(draft: TextDraft, nullObjects: List<NullObject>, onChange: (TextDraft) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val style = draft.style
    fun setStyle(s: TextStyleSpec) = onChange(draft.copy(style = s))
    val valid = draft.text.isNotBlank()

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(MfColors.Surface)
            .navigationBarsPadding().imePadding().padding(bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Annuler", tint = Color.White) }
            OutlinedTextField(
                value = draft.text, onValueChange = { onChange(draft.copy(text = it)) }, singleLine = true,
                placeholder = { Text("Saisie du texte", color = MfColors.TextMuted) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedContainerColor = MfColors.Card, unfocusedContainerColor = MfColors.Card,
                    focusedBorderColor = MfColors.Active, unfocusedBorderColor = MfColors.Outline, cursorColor = MfColors.Active,
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onConfirm, enabled = valid) {
                Icon(Icons.Rounded.Check, "Valider", tint = if (valid) MfColors.Active else MfColors.TextMuted)
            }
        }
        Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MfColors.Outline))
        Spacer(Modifier.height(8.dp))
        DrawerTabs(listOf("Styles", "Polices", "Position", "Parent"), tab) { tab = it }
        Spacer(Modifier.height(8.dp))

        Column(Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Presets.forEach { p ->
                            val on = style.preset == p.preset
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)).background(MfColors.Card)
                                        .border(if (on) 2.dp else 1.dp, if (on) MfColors.Violet else MfColors.Outline, RoundedCornerShape(14.dp))
                                        .pressable(role = null) { setStyle(style.copy(preset = p.preset, font = p.font, colorArgb = p.color)) },
                                    contentAlignment = Alignment.Center,
                                ) { Text("Aa", style = previewStyle(TextStyleSpec(p.preset, p.font, p.color), 24f)) }
                                Spacer(Modifier.height(4.dp))
                                Text(p.label, color = if (on) Color.White else MfColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Palette.forEach { argb ->
                            val on = style.colorArgb == argb
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(Color(argb))
                                    .border(if (on) 3.dp else 1.dp, if (on) MfColors.Active else MfColors.Outline, CircleShape)
                                    .pressable(role = null) { setStyle(style.copy(colorArgb = argb)) },
                            )
                        }
                    }
                    LabeledSlider("Taille", "${style.size.toInt()}", style.size, 28f..140f, onChange = { setStyle(style.copy(size = it)) })
                }
                1 -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Fonts.forEach { (key, label) ->
                        val on = style.font == key
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) MfColors.Active.copy(alpha = .22f) else MfColors.Card)
                                .border(if (on) 2.dp else 1.dp, if (on) MfColors.Active else MfColors.Outline, RoundedCornerShape(12.dp))
                                .pressable(role = null) { setStyle(style.copy(font = key)) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) { Text(label, color = Color.White, style = TextStyle(fontFamily = familyFor(key), fontSize = 18.sp)) }
                    }
                }
                2 -> LabeledSlider(
                    "Position verticale", if (style.posY > 0.05f) "Haut" else if (style.posY < -0.05f) "Bas" else "Centre",
                    style.posY, -0.9f..0.9f, onChange = { setStyle(style.copy(posY = it)) },
                )
                else -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParentChoice("Aucun", draft.parentId == null) { onChange(draft.copy(parentId = null)) }
                    nullObjects.forEach { node -> ParentChoice(node.name, draft.parentId == node.id) { onChange(draft.copy(parentId = node.id)) } }
                }
            }
        }
    }
}

@Composable
private fun ParentChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(12.dp))
        .background(if (selected) MfColors.Active.copy(alpha = .22f) else MfColors.Card)
        .border(1.dp, if (selected) MfColors.Active else MfColors.Outline, RoundedCornerShape(12.dp))
        .pressable(role = null, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp))
}

/** Texts visible at the playhead, drawn over the video preview; the draft being edited replaces its original. */
@Composable
internal fun BoxScope.PreviewTexts(state: EditorUiState, draft: TextDraft?) {
    state.textOverlays
        .filter { it.id != draft?.targetId && state.positionMs in it.startMs..it.endMs }
        .forEach {
            val transform = SceneGraph.resolve(it.animation.at(state.positionMs), it.parentId, state.nullObjects, state.positionMs)
            PreviewText(it.text, it.style, transform)
        }
    if (draft != null) {
        val local = draft.targetId?.let { id -> state.textOverlays.firstOrNull { it.id == id }?.animation?.at(state.positionMs) } ?: Keyframe(state.positionMs)
        PreviewText(draft.text, draft.style, SceneGraph.resolve(local, draft.parentId, state.nullObjects, state.positionMs))
    }
}

@Composable
private fun BoxScope.PreviewText(text: String, style: TextStyleSpec, transform: Keyframe) {
    Box(
        Modifier.fillMaxSize().padding(12.dp),
        contentAlignment = BiasAlignment(
            (transform.x / 540f).coerceIn(-1f, 1f),
            (-style.posY - transform.y / 960f).coerceIn(-1f, 1f),
        ),
    ) {
        Text(text.ifBlank { " " }, modifier = Modifier.graphicsLayer {
            scaleX = transform.scale.coerceIn(.01f, 20f)
            scaleY = transform.scale.coerceIn(.01f, 20f)
            rotationZ = transform.rotation
            alpha = transform.opacity.coerceIn(0f, 1f)
        }, style = previewStyle(style, (style.size / 3f).coerceIn(12f, 56f)), textAlign = TextAlign.Center, maxLines = 3)
    }
}
