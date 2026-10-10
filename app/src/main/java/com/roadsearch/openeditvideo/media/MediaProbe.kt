package com.roadsearch.openeditvideo.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap

object MediaProbe {
    fun durationMs(context: Context, uri: Uri, fallbackMs: Long = 5_000L): Long =
        durationMsOrNull(context, uri) ?: fallbackMs.coerceAtLeast(250L)

    /** True for still images (photos, generated backgrounds): MIME type first, file extension as a fallback. */
    fun isImage(context: Context, uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?: MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                ?.takeIf { it.isNotBlank() }
                ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) }
        return mime?.startsWith("image/") == true
    }

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
