package com.roadsearch.openeditvideo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import android.net.Uri

/** Cached, asynchronous filmstrip thumbnails. Coil 3's video decoder backs each frame request. */
@Composable
fun ThumbnailStrip(
    uri: Uri,
    startMs: Long,
    endMs: Long,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val frames = count.coerceIn(2, 16)
    Row(modifier = modifier.background(Color.Black.copy(alpha = .15f))) {
        repeat(frames) { index ->
            val fraction = if (frames == 1) 0f else index / (frames - 1).toFloat()
            val frameMs = (startMs + ((endMs - startMs).coerceAtLeast(0L) * fraction)).toLong()
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .videoFrameMillis(frameMs)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(Dp(52f)).fillMaxHeight(),
            )
        }
    }
}
