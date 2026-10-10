package com.roadsearch.openeditvideo.export

import com.roadsearch.openeditvideo.model.BlendMode
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.Transition
import com.roadsearch.openeditvideo.model.TransitionType
import com.roadsearch.openeditvideo.render.CompositorPlan
import com.roadsearch.openeditvideo.render.CompositorPlanBuilder
import java.io.File
import java.util.Locale

/**
 * Builds an actual FFmpeg filter graph for the advanced backend.
 * The command is deterministic and shell-free; execution is performed by the FFmpeg bridge.
 */
object FfmpegTimelineCommandBuilder {
    data class Command(val args: List<String>, val reasons: List<String>)

    fun build(state: EditorUiState, output: File): Command {
        val plan = CompositorPlanBuilder.build(state)
        require(plan.layers.isNotEmpty()) { "Aucune piste vidéo" }
        val reasons = buildList {
            plan.layers.filter { it.blendMode != BlendMode.NORMAL }.forEach { add("blend=${it.blendMode} clip=${it.clipId}") }
            plan.transitions.filter { it.type != TransitionType.CUT }.forEach { add("transition=${it.type} ${it.durationMs}ms") }
        }
        val args = mutableListOf<String>("-y")
        state.clips.sortedBy { it.id }.forEach { clip ->
            // A photo is an endless looped image: bound it to the time it is shown.
            if (isStill(clip)) args += listOf("-loop", "1", "-t", seconds((clip.endMs - clip.startMs).coerceAtLeast(1L)))
            args += listOf("-i", clip.uri.toString())
        }
        state.audioClips.sortedBy { it.id }.forEach { args += listOf("-i", it.uri.toString()) }
        args += listOf("-filter_complex", buildFilterGraph(state, plan))
        args += listOf("-map", "[vout]")
        if (state.audioClips.isNotEmpty() || state.clips.any { it.track == 0 }) args += listOf("-map", "[aout]")
        args += listOf("-c:v", "mpeg4", "-q:v", "3", "-c:a", "aac", output.absolutePath)
        return Command(args, reasons)
    }

    private fun buildFilterGraph(state: EditorUiState, plan: CompositorPlan): String {
        val videoClips = state.clips.sortedBy { it.id }
        val indexByClip = videoClips.mapIndexed { index, clip -> clip.id to index }.toMap()
        val parts = mutableListOf<String>()
        val base = plan.layers.firstOrNull { it.track == 0 }
            ?: error("La piste V1 est obligatoire pour le backend avancé")
        val baseIndex = indexByClip.getValue(base.clipId)
        val baseDur = seconds(base.durationMs)
        val baseClip = videoClips.first { it.id == base.clipId }
        parts += "[$baseIndex:v]trim=start=${seconds(sourceStart(baseClip))}:duration=$baseDur,setpts=PTS-STARTPTS[base0]"
        var current = "base0"
        var nextBase = 1
        val upper = plan.layers.filter { it.clipId != base.clipId }.sortedBy { it.zIndex }
        for (layer in upper) {
            val input = indexByClip.getValue(layer.clipId)
            val start = seconds(layer.timelineStartMs)
            val end = seconds(layer.timelineStartMs + layer.durationMs)
            val label = "ov${nextBase}"
            val fg = "fg${nextBase}"
            val placement = "enable='between(t,$start,$end)':eof_action=pass:shortest=0"
            if (layer.blendMode == BlendMode.NORMAL) {
                val clip = videoClips.first { it.id == layer.clipId }
                parts += "[$input:v]trim=start=${seconds(sourceStart(clip))}:duration=${seconds(layer.durationMs)},setpts=PTS-STARTPTS+$start/TB[$fg]"
                parts += "[$current][$fg]overlay=x=0:y=0:$placement[$label]"
            } else {
                // FFmpeg's blend filter operates on aligned full-frame inputs; this branch intentionally
                // uses it for full-frame advanced blending. Positioned advanced blends are rejected by the analyzer.
                val clip = videoClips.first { it.id == layer.clipId }
                parts += "[$input:v]trim=start=${seconds(sourceStart(clip))}:duration=${seconds(layer.durationMs)},setpts=PTS-STARTPTS[$fg]"
                val blend = ffmpegBlend(layer.blendMode)
                parts += "[$current][$fg]blend=all_mode=$blend:all_opacity=1[$label]"
            }
            current = label
            nextBase++
        }
        parts += "[$current]format=yuv420p[vout]"

        val audioInputs = mutableListOf<String>()
        state.clips.filter { it.track == 0 && !isStill(it) }.forEach { clip ->
            val input = indexByClip.getValue(clip.id)
            val start = seconds(clip.timelineStartMs)
            val duration = seconds((clip.endMs - clip.startMs).coerceAtLeast(0L))
            val label = "va${audioInputs.size}"
            parts += "[$input:a]atrim=start=${seconds(clip.startMs)}:duration=$duration,asetpts=PTS-STARTPTS,adelay=${clip.timelineStartMs}:all=1,volume=${clip.volume.coerceIn(0f, 1f)}[$label]"
            audioInputs += "[$label]"
        }
        val audioOffset = videoClips.size
        state.audioClips.sortedBy { it.id }.forEachIndexed { idx, clip ->
            val input = audioOffset + idx
            val label = "aa${audioInputs.size}"
            val duration = seconds((clip.endMs - clip.startMs).coerceAtLeast(0L))
            parts += "[$input:a]atrim=duration=$duration,asetpts=PTS-STARTPTS,adelay=${clip.timelineStartMs}:all=1,volume=${clip.volume.coerceIn(0f, 1f)}[$label]"
            audioInputs += "[$label]"
        }
        if (audioInputs.isEmpty()) {
            parts += "anullsrc=r=48000:cl=stereo:d=${seconds(plan.durationMs)}[aout]"
        } else {
            parts += audioInputs.joinToString("") + "amix=inputs=${audioInputs.size}:duration=longest:dropout_transition=0,aresample=async=1:first_pts=0[aout]"
        }

        return parts.joinToString(";")
    }

    private fun ffmpegBlend(mode: BlendMode): String = when (mode) {
        BlendMode.NORMAL -> "normal"
        BlendMode.ADD -> "addition"
        BlendMode.MULTIPLY -> "multiply"
        BlendMode.SCREEN -> "screen"
        BlendMode.OVERLAY -> "overlay"
        BlendMode.DARKEN -> "darken"
        BlendMode.LIGHTEN -> "lighten"
    }

    private fun isStill(clip: com.roadsearch.openeditvideo.model.VideoClip): Boolean =
        clip.sourceDurationMs >= com.roadsearch.openeditvideo.model.STILL_SOURCE_MS

    /** Stills have no source timeline: they always start at 0. */
    private fun sourceStart(clip: com.roadsearch.openeditvideo.model.VideoClip): Long = if (isStill(clip)) 0L else clip.startMs

    private fun seconds(ms: Long): String = String.format(Locale.US, "%.6f", ms.coerceAtLeast(0L) / 1000.0)
}
