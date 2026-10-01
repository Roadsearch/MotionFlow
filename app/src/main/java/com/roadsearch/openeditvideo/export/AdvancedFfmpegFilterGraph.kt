package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState

/** Pure filter-graph generator. It does not execute FFmpeg. */
object AdvancedFfmpegFilterGraph {
    fun blendMode(mode: BlendMode): String = when (mode) {
        BlendMode.NORMAL -> "normal"
        BlendMode.ADD -> "addition"
        BlendMode.MULTIPLY -> "multiply"
        BlendMode.SCREEN -> "screen"
        BlendMode.OVERLAY -> "overlay"
        BlendMode.DARKEN -> "darken"
        BlendMode.LIGHTEN -> "lighten"
    }

    /**
     * Generates a deterministic two-input overlay expression. More inputs can be folded by
     * repeatedly applying this operation in timeline order.
     */
    fun twoInputOverlay(backgroundLabel: String, foregroundLabel: String, outputLabel: String, mode: BlendMode, x: Int = 0, y: Int = 0): String =
        "[$backgroundLabel][$foregroundLabel]blend=all_mode=${blendMode(mode)}:all_opacity=1:shortest=0[$outputLabel]"
            .let { base -> if (x == 0 && y == 0) base else "[$backgroundLabel][$foregroundLabel]overlay=$x:$y:format=auto[$outputLabel]" }

    fun diagnostic(state: EditorUiState): List<String> = buildList {
        state.blendModes.filterValues { it != BlendMode.NORMAL }.forEach { (id, mode) ->
            add("clip=$id blend=${blendMode(mode)}")
        }
    }
}
