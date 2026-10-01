package com.roadsearch.openeditvideo.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

object MediaProbe {
    fun durationMs(context: Context, uri: Uri, fallbackMs: Long = 5_000L): Long =
        durationMsOrNull(context, uri) ?: fallbackMs.coerceAtLeast(250L)

    fun durationMsOrNull(context: Context, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(250L)
        } catch (_: Throwable) {
            null
        } finally {
            retriever.release()
        }
    }
}
