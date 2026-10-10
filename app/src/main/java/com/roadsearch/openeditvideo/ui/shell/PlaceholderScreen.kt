package com.roadsearch.openeditvideo.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.ui.theme.MfColors

/** Temporary screen for sections that arrive in later tasks (Modèles, Moi, Outils IA). */
@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title.uppercase(), color = Color.White, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Text("Bientôt disponible", color = MfColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}
