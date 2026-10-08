package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.model.EditorUiState
import java.io.File

/** Stable export port. Media3 is the default backend; advanced native backends can implement this later. */
interface VideoExporter {
    suspend fun export(
        state: EditorUiState,
        output: File,
        settings: ExportSettings = ExportSettings(),
        onProgress: (Float) -> Unit = {},
    )
}
