package com.roadsearch.openeditvideo.ui.drawers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.ui.theme.MfColors

private val Tips = listOf(
    "Parcourir" to "Faites glisser la timeline avec un doigt : la ligne blanche reste au centre et indique l'instant affiché.",
    "Sélectionner un clip" to "Touchez-le. Une fois sélectionné, glissez son corps pour le déplacer, ou les poignées blanches pour ajuster sa durée. Glisser un clip non sélectionné fait défiler la timeline.",
    "Couper" to "Placez la ligne blanche, sélectionnez le clip, puis « Diviser ». Les boutons de part et d'autre de la ligne coupent et suppriment ce qui est à gauche ou à droite.",
    "Image clé  ◇+" to "Place un point d'animation (position, échelle, rotation, opacité) à l'instant de la ligne blanche. Réglez-la dans « Animation ».",
    "Couches" to "Le bouton couches liste les pistes vidéo. Glissez ☰ pour changer l'ordre d'empilement. Les superpositions peuvent être étirées jusqu'au début ou à la fin.",
    "Format et résolution" to "En haut : choisissez 9:16, 16:9 ou 1:1, puis la résolution d'export. D'autres réglages (images/s, qualité) sont dans « Exporter ».",
    "Texte et musique" to "Les pistes vides « + Ajouter du texte / de la musique » ouvrent leurs panneaux. Le texte s'affiche en direct sur l'aperçu pendant que vous le modifiez.",
    "Annuler / rétablir" to "Les flèches sous l'aperçu annulent ou rétablissent vos dernières actions.",
)

@Composable
internal fun HelpDrawer(onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
        DrawerHeader("Aide", onClose)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Tips.forEach { (title, body) ->
                Row {
                    Text("•", color = MfColors.Active, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                        Text(body, color = MfColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}
