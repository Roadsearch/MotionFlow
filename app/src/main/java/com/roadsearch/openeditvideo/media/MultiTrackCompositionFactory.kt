package com.roadsearch.openeditvideo.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.text.SpannableString
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList
import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.TextOverlay as TextOverlayModel
import com.roadsearch.openeditvideo.model.VideoClip

/** Builds an absolute-time Media3 composition from the editable NLE timeline. */
@UnstableApi
class MultiTrackCompositionFactory(private val context: Context) {
    companion object {
        const val DEFAULT_WIDTH = 1080
        const val DEFAULT_HEIGHT = 1920
    }

    /** Solid-color placeholder images are generated at runtime into the private cache,
     *  so no binary drawable assets need to live in the repository. */
    private fun placeholderImage(name: String, argb: Int): Uri {
        val dir = java.io.File(context.cacheDir, "generated").apply { mkdirs() }
        val file = java.io.File(dir, "$name.png")
        if (!file.exists()) {
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(argb) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return Uri.fromFile(file)
    }

    fun build(state: EditorUiState): Composition {
        val clips = state.clips
            .filter { state.trackStates[it.track]?.hidden != true }
            .sortedWith(compareBy<VideoClip> { it.track }.thenBy { it.timelineStartMs }.thenBy { it.id })
        require(clips.isNotEmpty()) { "Aucun clip vidéo à exporter" }
        val durationMs = maxOf(
            state.durationMs,
            clips.maxOf { it.timelineStartMs + clipDurationMs(it) },
            state.audioClips.maxOfOrNull { it.timelineStartMs + audioDurationMs(it) } ?: 0L,
            state.textOverlays.maxOfOrNull { it.endMs } ?: 0L,
        )
        require(durationMs > 0L) { "La timeline est vide" }

        val videoPlans = buildList<VideoInputPlan> {
            add(VideoInputPlan.Background(durationMs))
            clips.forEach { add(VideoInputPlan.Clip(it, clipDurationMs(it))) }
            state.textOverlays
                .filter { it.endMs > it.startMs && it.text.isNotBlank() }
                .forEach { add(VideoInputPlan.Text(it)) }
        }

        val videoSequences = videoPlans.map { plan ->
            when (plan) {
                is VideoInputPlan.Background -> buildBlackBackgroundSequence(plan.durationMs)
                is VideoInputPlan.Clip -> buildVideoSequence(state, plan.clip, plan.durationMs, durationMs)
                is VideoInputPlan.Text -> buildTextSequence(plan.overlay, durationMs)
            }
        }
        val audioSequences = state.audioClips
            .filter { it.endMs > it.startMs }
            .map { buildAudioSequence(it, durationMs) }

        return Composition.Builder(videoSequences + audioSequences)
            .setVideoCompositorSettings(TimelineVideoCompositorSettings(videoPlans))
            .build()
    }

    private fun buildBlackBackgroundSequence(durationMs: Long): EditedMediaItemSequence {
        val item = MediaItem.Builder()
            .setUri(placeholderImage("openedit_black", 0xFF000000.toInt()))
            .setImageDurationMs(durationMs)
            .build()
        return EditedMediaItemSequence.withVideoFrom(EditedMediaItem.Builder(item).build())
    }

    private fun buildVideoSequence(state: EditorUiState, clip: VideoClip, clipDurationMs: Long, timelineDurationMs: Long): EditedMediaItemSequence {
        val prefixUs = clip.timelineStartMs.coerceAtLeast(0L) * 1000L
        val suffixUs = (timelineDurationMs - clip.timelineStartMs - clipDurationMs).coerceAtLeast(0L) * 1000L
        val itemEffects = MediaEngine(context).effectsForClipForComposition(state, clip)
        val sourceEnd = clip.startMs + clipDurationMs
        val trackMuted = state.trackStates[clip.track]?.muted == true
        val trackTypes = if (clip.track == 0 && !trackMuted) setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO) else setOf(C.TRACK_TYPE_VIDEO)
        val item = EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs.coerceAtLeast(0L))
                        .setEndPositionMs(sourceEnd)
                        .build()
                )
                .build()
        ).setEffects(itemEffects).build()
        return EditedMediaItemSequence.Builder(trackTypes).apply {
            if (prefixUs > 0L) addGap(prefixUs)
            addItem(item)
            if (suffixUs > 0L) addGap(suffixUs)
        }.build()
    }

    private fun buildTextSequence(overlay: TextOverlayModel, timelineDurationMs: Long): EditedMediaItemSequence {
        val duration = (overlay.endMs - overlay.startMs).coerceAtLeast(1L)
        val transparent = MediaItem.Builder()
            .setUri(placeholderImage("openedit_transparent", 0x00000000))
            .setImageDurationMs(duration)
            .build()
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f, 0f)
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(1f, 1f)
            .build()
        val text = TextOverlay.createStaticTextOverlay(SpannableString(overlay.text), settings)
        val item = EditedMediaItem.Builder(transparent)
            .setEffects(Effects(emptyList(), ImmutableList.of(OverlayEffect(ImmutableList.of(text)))))
            .build()
        val suffixUs = (timelineDurationMs - overlay.endMs).coerceAtLeast(0L) * 1000L
        return EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).apply {
            if (overlay.startMs > 0L) addGap(overlay.startMs * 1000L)
            addItem(item)
            if (suffixUs > 0L) addGap(suffixUs)
        }.build()
    }

    private fun buildAudioSequence(audio: com.roadsearch.openeditvideo.model.AudioClip, timelineDurationMs: Long): EditedMediaItemSequence {
        val durationMs = audioDurationMs(audio)
        val item = EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(audio.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(audio.startMs.coerceAtLeast(0L))
                        .setEndPositionMs(audio.startMs + durationMs)
                        .build()
                ).build()
        ).setEffects(Effects(listOf(MediaEngine.constantGainProcessor(audio.volume)))).build()
        val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        if (audio.timelineStartMs > 0L) builder.addGap(audio.timelineStartMs * 1000L)
        builder.addItem(item)
        val suffix = (timelineDurationMs - audio.timelineStartMs - durationMs).coerceAtLeast(0L)
        if (suffix > 0L) builder.addGap(suffix * 1000L)
        return builder.build()
    }

    private fun clipDurationMs(clip: VideoClip): Long =
        (clip.endMs - clip.startMs).takeIf { it > 0L }
            ?: clip.sourceDurationMs.takeIf { it > clip.startMs }?.minus(clip.startMs)
            ?: TimelineMath.MIN_CLIP_DURATION_MS

    private fun audioDurationMs(audio: com.roadsearch.openeditvideo.model.AudioClip): Long =
        (audio.endMs - audio.startMs).coerceAtLeast(1L)
}

private sealed interface VideoInputPlan {
    data class Background(val durationMs: Long) : VideoInputPlan
    data class Clip(val clip: VideoClip, val durationMs: Long) : VideoInputPlan
    data class Text(val overlay: TextOverlayModel) : VideoInputPlan
}

@UnstableApi
private class TimelineVideoCompositorSettings(
    private val plans: List<VideoInputPlan>,
) : androidx.media3.common.VideoCompositorSettings {
    override fun getOutputSize(inputSizes: List<Size>): Size =
        Size(MultiTrackCompositionFactory.DEFAULT_WIDTH, MultiTrackCompositionFactory.DEFAULT_HEIGHT)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): StaticOverlaySettings {
        val plan = plans.getOrNull(inputId) ?: return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        val globalMs = presentationTimeUs / 1000L
        return when (plan) {
            is VideoInputPlan.Background -> StaticOverlaySettings.Builder().setAlphaScale(1f).build()
            is VideoInputPlan.Clip -> {
                val clip = plan.clip
                val active = globalMs >= clip.timelineStartMs && globalMs < clip.timelineStartMs + plan.durationMs
                if (!active) return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
                val keyframe = clip.keyframesAt(globalMs)
                val x = (keyframe.x / (MultiTrackCompositionFactory.DEFAULT_WIDTH / 2f)).coerceIn(-1f, 1f)
                val y = (-keyframe.y / (MultiTrackCompositionFactory.DEFAULT_HEIGHT / 2f)).coerceIn(-1f, 1f)
                val scale = keyframe.scale.coerceIn(0.01f, 20f)
                StaticOverlaySettings.Builder()
                    .setBackgroundFrameAnchor(x, y)
                    .setOverlayFrameAnchor(0f, 0f)
                    .setScale(scale, scale)
                    .setRotationDegrees(clip.effects.rotation + keyframe.rotation)
                    .setAlphaScale(keyframe.opacity.coerceIn(0f, 1f))
                    .build()
            }
            is VideoInputPlan.Text -> {
                if (globalMs !in plan.overlay.startMs until plan.overlay.endMs) {
                    StaticOverlaySettings.Builder().setAlphaScale(0f).build()
                } else {
                    StaticOverlaySettings.Builder()
                        .setBackgroundFrameAnchor(0f, 0f)
                        .setOverlayFrameAnchor(0f, 0f)
                        .setAlphaScale(1f)
                        .build()
                }
            }
        }
    }
}
