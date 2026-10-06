package com.roadsearch.openeditvideo.ui.drawers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TransitionType
import com.roadsearch.openeditvideo.ui.EditorViewModel
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.theme.MfColors

private fun label(t: TransitionType) = when (t) {
    TransitionType.CUT -> "Aucune"
    TransitionType.CROSS_FADE -> "Fondu enchaîné"
    TransitionType.FADE_THROUGH -> "Fondu noir"
    TransitionType.WIPE_LEFT -> "Balayage ←"
    TransitionType.WIPE_RIGHT -> "Balayage →"
}

/** Transition between two adjacent main-track clips (opened from the ⋈ button). */
@Composable
internal fun TransitionDrawer(state: EditorUiState, vm: EditorViewModel, pair: Pair<Long, Long>?, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        DrawerHeader("Transition", onClose)
        val valid = pair != null && state.clips.any { it.id == pair.first } && state.clips.any { it.id == pair.second }
        if (!valid || pair == null) {
            EmptyNote("Touchez le bouton ⋈ entre deux clips de la piste principale.")
        } else {
            val existing = state.transitions.firstOrNull { it.fromClipId == pair.first && it.toClipId == pair.second }
            var type by remember(pair) { mutableStateOf(existing?.type ?: TransitionType.CUT) }
            var duration by remember(pair) { mutableFloatStateOf((existing?.durationMs ?: 500L).toFloat()) }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransitionType.entries.forEach { t ->
                    val on = t == type
                    androidx.compose.foundation.layout.Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) MfColors.Active.copy(alpha = .22f) else MfColors.Card)
                            .border(if (on) 2.dp else 1.dp, if (on) MfColors.Active else MfColors.Outline, RoundedCornerShape(12.dp))
                            .pressable(role = null) { type = t }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) { Text(label(t), color = Color.White, style = MaterialTheme.typography.labelLarge) }
                }
            }
            if (type != TransitionType.CUT) {
                Spacer(Modifier.height(8.dp))
                LabeledSlider("Durée", "%.1f s".format(duration / 1000f), duration, 100f..3000f, onChange = { duration = it })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "⚠ Le moteur ne rend pas encore les transitions : ni dans l'aperçu, ni à l'export. Un projet qui en contient ne peut pas être exporté tant qu'elles ne sont pas retirées.",
                color = Color(0xFFFFB74D), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFB74D)).padding(12.dp),
            )
            Spacer(Modifier.height(12.dp))
            GradientButton("Appliquer") { vm.setTransition(pair.first, pair.second, type, duration.toLong()); onClose() }
        }
    }
}
