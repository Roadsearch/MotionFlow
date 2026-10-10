package com.roadsearch.openeditvideo.ui.timeline

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.ui.components.pressable
import com.roadsearch.openeditvideo.ui.theme.MfColors

/** Source (uri, source time) of the main-track frame shown at timeline time [timeMs]; null for an empty project. */
internal fun EditorUiState.frameAt(timeMs: Long): Pair<Uri, Long>? {
    val main = clips.minOfOrNull { it.track } ?: return null
    val onMain = clips.filter { it.track == main }
    val clip = onMain.firstOrNull { c ->
        val length = TimelineMath.duration(c, c.sourceDurationMs.coerceAtLeast(durationMs))
        timeMs >= c.timelineStartMs && timeMs < c.timelineStartMs + length
    } ?: onMain.minByOrNull { it.timelineStartMs } ?: return null
    return clip.uri to (clip.startMs + (timeMs - clip.timelineStartMs).coerceAtLeast(0L))
}

/** "Couverture" tile in the leading margin of the main track: shows the current cover frame. */
@Composable
internal fun CoverTile(state: EditorUiState, onClick: () -> Unit) {
    val context = LocalContext.current
    val frame = state.frameAt(state.coverMs)
    Box(
        Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(MfColors.CardHigh).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (frame != null) {
            val request = remember(frame) { ImageRequest.Builder(context).data(frame.first).videoFrameMillis(frame.second).build() }
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text("Couverture", color = Color.White, fontSize = 9.sp, maxLines = 1)
        }
    }
}
