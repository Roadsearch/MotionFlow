package com.roadsearch.openeditvideo.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import androidx.core.content.res.ResourcesCompat
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
import com.roadsearch.openeditvideo.model.STILL_SOURCE_MS
import com.roadsearch.openeditvideo.model.VideoClip
import com.roadsearch.openeditvideo.model.keyframesAt
import com.roadsearch.openeditvideo.model.at

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

    fun build(state: EditorUiState, settings: com.roadsearch.openeditvideo.export.ExportSettings = com.roadsearch.openeditvideo.export.ExportSettings()): Composition {
        val clips = state.clips
            .filter { state.trackStates[it.track]?.hidden != true }
            .sortedWith(compareBy<VideoClip> { it.track }.thenBy { it.timelineStartMs }.thenBy { it.id })
        require(clips.isNotEmpty()) { "Aucun clip vidéo à exporter" }
        // Transitions become opacity ramps (and extra source frames for cross-fades).
        val prepared = TransitionRenderPlan.prepare(state.transitions, clips)
        val renderClips = prepared.clips.sortedWith(compareBy<VideoClip> { it.track }.thenBy { it.timelineStartMs }.thenBy { it.id })
        val durationMs = maxOf(
            state.durationMs,
            renderClips.maxOf { it.timelineStartMs + clipDurationMs(it) },
            state.audioClips.maxOfOrNull { it.timelineStartMs + audioDurationMs(it) } ?: 0L,
            state.textOverlays.maxOfOrNull { it.endMs } ?: 0L,
        )
        require(durationMs > 0L) { "La timeline est vide" }

        val videoPlans = buildList<VideoInputPlan> {
            add(VideoInputPlan.Background(durationMs))
            renderClips.forEach { add(VideoInputPlan.Clip(it, clipDurationMs(it), prepared.fades[it.id])) }
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

        val (canvasW, canvasH) = settings.canvasSize(state.aspect)
        val outputSize = Size(canvasW, canvasH)
        val builder = Composition.Builder(videoSequences + audioSequences)
            .setVideoCompositorSettings(TimelineVideoCompositorSettings(videoPlans, outputSize, state.nullObjects))
        // Frame rate is a ceiling: frames are dropped to reach 24/30 fps; 60 keeps the source rate.
        if (settings.fps < 60) {
            builder.setEffects(Effects(emptyList(), listOf<androidx.media3.common.Effect>(androidx.media3.effect.FrameDropEffect.createDefaultFrameDropEffect(settings.fps.toFloat()))))
        }
        return builder.build()
    }

    private fun buildBlackBackgroundSequence(durationMs: Long): EditedMediaItemSequence {
        val item = MediaItem.Builder()
            .setUri(placeholderImage("openedit_black", 0xFF000000.toInt()))
            .setImageDurationMs(durationMs)
            .build()
        return EditedMediaItemSequence.withVideoFrom(listOf(EditedMediaItem.Builder(item).build()))
    }

    private fun buildVideoSequence(state: EditorUiState, clip: VideoClip, clipDurationMs: Long, timelineDurationMs: Long): EditedMediaItemSequence {
        val prefixUs = clip.timelineStartMs.coerceAtLeast(0L) * 1000L
        val suffixUs = (timelineDurationMs - clip.timelineStartMs - clipDurationMs).coerceAtLeast(0L) * 1000L
        val itemEffects = MediaEngine(context).effectsForClipForComposition(state, clip)
        val sourceEnd = clip.startMs + clipDurationMs
        val trackMuted = state.trackStates[clip.track]?.muted == true
        val still = clip.sourceDurationMs >= STILL_SOURCE_MS
        val trackTypes = if (!still && clip.track == 0 && !trackMuted) setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO) else setOf(C.TRACK_TYPE_VIDEO)
        val mediaItem = if (still) {
            // Photos and generated backgrounds have no timeline of their own: Media3 needs an explicit duration.
            MediaItem.Builder().setUri(clip.uri).setImageDurationMs(clipDurationMs.coerceAtLeast(1L)).build()
        } else {
            MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs.coerceAtLeast(0L))
                        .setEndPositionMs(sourceEnd)
                        .build()
                )
                .build()
        }
        val effects = if (still) Effects(emptyList(), itemEffects.videoEffects) else itemEffects
        val item = EditedMediaItem.Builder(mediaItem).setEffects(effects).build()
        return EditedMediaItemSequence.Builder(trackTypes).apply {
            if (prefixUs > 0L) addGap(prefixUs)
            addItem(item)
            if (suffixUs > 0L) addGap(suffixUs)
        }.build()
    }

    /** Applies the drawer-selected style (font, colour, size, preset) to the exported text. */
    private fun styledText(overlay: TextOverlayModel): SpannableString {
        val st = overlay.style
        val base = when (st.font) {
            "bebas" -> ResourcesCompat.getFont(context, com.roadsearch.openeditvideo.R.font.bebas_neue_regular)
            "inter" -> ResourcesCompat.getFont(context, com.roadsearch.openeditvideo.R.font.inter_variable)
            "serif" -> Typeface.SERIF
            "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
            else -> Typeface.SANS_SERIF
        } ?: Typeface.SANS_SERIF
        val face = when (st.preset) {
            com.roadsearch.openeditvideo.model.TextPreset.CLASSIC, com.roadsearch.openeditvideo.model.TextPreset.BOLD3D -> Typeface.create(base, Typeface.BOLD)
            com.roadsearch.openeditvideo.model.TextPreset.SCRIPT -> Typeface.create(base, Typeface.ITALIC)
            com.roadsearch.openeditvideo.model.TextPreset.NEON -> base
        }
        return SpannableString(overlay.text).apply {
            val flag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            setSpan(ForegroundColorSpan(st.colorArgb), 0, length, flag)
            setSpan(AbsoluteSizeSpan(st.size.toInt().coerceIn(12, 220)), 0, length, flag)
            setSpan(TypefaceSpan(face), 0, length, flag)
        }
    }

    private fun buildTextSequence(overlay: TextOverlayModel, timelineDurationMs: Long): EditedMediaItemSequence {
        val duration = (overlay.endMs - overlay.startMs).coerceAtLeast(1L)
        val transparent = MediaItem.Builder()
            .setUri(placeholderImage("openedit_transparent", 0x00000000))
            .setImageDurationMs(duration)
            .build()
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f, overlay.style.posY.coerceIn(-1f, 1f))
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(1f, 1f)
            .build()
        val text = TextOverlay.createStaticTextOverlay(styledText(overlay), settings)
        val item = EditedMediaItem.Builder(transparent)
            .setEffects(Effects(emptyList(), listOf<androidx.media3.common.Effect>(OverlayEffect(ImmutableList.of<androidx.media3.effect.TextureOverlay>(text)))))
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
        ).setEffects(Effects(listOf(MediaEngine.constantGainProcessor(audio.volume)), emptyList())).build()
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
    data class Clip(val clip: VideoClip, val durationMs: Long, val fades: TransitionRenderPlan.ClipFades? = null) : VideoInputPlan
    data class Text(val overlay: TextOverlayModel) : VideoInputPlan
}

@UnstableApi
private class TimelineVideoCompositorSettings(
    private val plans: List<VideoInputPlan>,
    private val outputSize: Size,
    private val nullObjects: List<com.roadsearch.openeditvideo.model.NullObject> = emptyList(),
) : androidx.media3.common.VideoCompositorSettings {
    override fun getOutputSize(inputSizes: List<Size>): Size = outputSize

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): StaticOverlaySettings {
        val plan = plans.getOrNull(inputId) ?: return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        val globalMs = presentationTimeUs / 1000L
        return when (plan) {
            is VideoInputPlan.Background -> StaticOverlaySettings.Builder().setAlphaScale(1f).build()
            is VideoInputPlan.Clip -> {
                val clip = plan.clip
                val active = globalMs >= clip.timelineStartMs && globalMs < clip.timelineStartMs + plan.durationMs
                if (!active) return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
                val keyframe = com.roadsearch.openeditvideo.scene.SceneGraph.resolve(clip.keyframesAt(globalMs), clip.parentId, nullObjects, globalMs)
                val x = (keyframe.x / (MultiTrackCompositionFactory.DEFAULT_WIDTH / 2f)).coerceIn(-1f, 1f)
                val y = (-keyframe.y / (MultiTrackCompositionFactory.DEFAULT_HEIGHT / 2f)).coerceIn(-1f, 1f)
                val scale = keyframe.scale.coerceIn(0.01f, 20f)
                StaticOverlaySettings.Builder()
                    .setBackgroundFrameAnchor(x, y)
                    .setOverlayFrameAnchor(0f, 0f)
                    .setScale(scale, scale)
                    .setRotationDegrees(clip.effects.rotation + keyframe.rotation)
                    .setAlphaScale(keyframe.opacity.coerceIn(0f, 1f) * (plan.fades?.factor(globalMs) ?: 1f))
                    .build()
            }
            is VideoInputPlan.Text -> {
                if (globalMs !in plan.overlay.startMs until plan.overlay.endMs) {
                    StaticOverlaySettings.Builder().setAlphaScale(0f).build()
                } else {
                    val transform = com.roadsearch.openeditvideo.scene.SceneGraph.resolve(
                        plan.overlay.animation.at(globalMs), plan.overlay.parentId, nullObjects, globalMs,
                    )
                    val x = (transform.x / (MultiTrackCompositionFactory.DEFAULT_WIDTH / 2f)).coerceIn(-1f, 1f)
                    val y = (-transform.y / (MultiTrackCompositionFactory.DEFAULT_HEIGHT / 2f)).coerceIn(-1f, 1f)
                    val posY = (plan.overlay.style.posY + y).coerceIn(-1f, 1f)
                    val scale = transform.scale.coerceIn(0.01f, 20f)
                    StaticOverlaySettings.Builder()
                        .setBackgroundFrameAnchor(x, posY)
                        .setOverlayFrameAnchor(0f, 0f)
                        .setScale(scale, scale)
                        .setRotationDegrees(transform.rotation)
                        .setAlphaScale(transform.opacity.coerceIn(0f, 1f))
                        .build()
                }
            }
        }
    }
}
