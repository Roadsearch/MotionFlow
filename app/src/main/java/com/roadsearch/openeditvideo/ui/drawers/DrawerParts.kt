package com.roadsearch.openeditvideo.ui.drawers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.theme.MfColors

@Composable
internal fun DrawerHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Fermer", tint = Color.White) }
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun DrawerTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier.padding(end = 20.dp).clickable { onSelect(i) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, color = if (on) Color.White else MfColors.TextMuted, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.height(2.dp).width(24.dp).background(if (on) MfColors.Active else Color.Transparent))
            }
        }
    }
}

@Composable
internal fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = MfColors.TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(valueText, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value, onValueChange = onChange, onValueChangeFinished = onFinished, valueRange = range, enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = MfColors.Active, activeTrackColor = MfColors.Active, inactiveTrackColor = MfColors.Outline),
        )
    }
}

@Composable
internal fun GradientButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(50.dp).clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(Color(0xFF8B4DEB), Color(0xFF5B7CFF), Color(0xFF00CEFD)))
                else Brush.horizontalGradient(listOf(MfColors.Card, MfColors.Card)),
            )
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) Color.White else MfColors.TextMuted, style = MaterialTheme.typography.titleSmall) }
}

@Composable
internal fun EmptyNote(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(text, color = MfColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
