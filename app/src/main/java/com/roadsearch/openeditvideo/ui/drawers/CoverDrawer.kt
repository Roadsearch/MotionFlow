package com.roadsearch.openeditvideo.ui.drawers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.timelineEndMs
import com.roadsearch.openeditvideo.ui.EditorViewModel
import com.roadsearch.openeditvideo.ui.formatTime
import com.roadsearch.openeditvideo.ui.theme.MfColors
import com.roadsearch.openeditvideo.ui.timeline.frameAt

/** Pick the frame used as project cover (shown in the Home list). */
@Composable
internal fun CoverDrawer(state: EditorUiState, vm: EditorViewModel, onClose: () -> Unit) {
    val end = remember(state.clips, state.audioClips, state.textOverlays) { state.timelineEndMs() }
    var draft by remember { mutableFloatStateOf(state.coverMs.coerceIn(0L, end).toFloat()) }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        DrawerHeader("Couverture", onClose)
        val frame = state.frameAt(draft.toLong())
        if (frame == null || end <= 0L) {
            EmptyNote("Importez une vidéo pour choisir une couverture.")
        } else {
            Box(
                Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.62f).aspectRatio(state.aspect.w.toFloat() / state.aspect.h)
                    .clip(RoundedCornerShape(14.dp)).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val request = remember(frame) { ImageRequest.Builder(context).data(frame.first).videoFrameMillis(frame.second).build() }
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            LabeledSlider("Image de couverture", formatTime(draft.toLong()), draft, 0f..end.toFloat(), onChange = { draft = it })
            Text(
                "Utiliser la position actuelle de la timeline (${formatTime(state.positionMs)})",
                color = MfColors.Active, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color.Transparent).androidxClickable { draft = state.positionMs.coerceIn(0L, end).toFloat() },
            )
            Spacer(Modifier.height(10.dp))
            GradientButton("Définir comme couverture") { vm.setCover(draft.toLong()); onClose() }
        }
    }
}

private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
