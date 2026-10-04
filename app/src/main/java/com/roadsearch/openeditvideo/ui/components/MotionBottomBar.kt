package com.roadsearch.openeditvideo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.ui.theme.MfColors

enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Rounded.Home),
    PROJECTS("Projets", Icons.Rounded.FolderOpen),
    TEMPLATES("Modèles", Icons.Rounded.Dashboard),
    PROFILE("Moi", Icons.Rounded.AccountCircle),
}

@Composable
fun MotionBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(MfColors.Surface)) {
        HorizontalDivider(color = MfColors.Outline)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            MainTab.entries.forEach { tab ->
                val active = tab == selected
                val tint = animateColorAsState(if (active) MfColors.Cyan else MfColors.TextMuted, label = "tabTint")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = active,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(tab) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint.value, modifier = Modifier.size(24.dp))
                    Text(tab.label, color = tint.value, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
