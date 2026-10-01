package com.roadsearch.openeditvideo.export

import android.content.Context
import com.roadsearch.openeditvideo.media.MediaEngine
import com.roadsearch.openeditvideo.model.EditorUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** Default hardware-accelerated exporter backed by AndroidX Media3 Transformer. */
class Media3VideoExporter @Inject constructor(
    @ApplicationContext context: Context,
) : VideoExporter {
    private val engine = MediaEngine(context)

    override suspend fun export(
        state: EditorUiState,
        output: File,
        onProgress: (Float) -> Unit,
    ) = engine.exportCompositionSuspend(state, output, onProgress)
}
