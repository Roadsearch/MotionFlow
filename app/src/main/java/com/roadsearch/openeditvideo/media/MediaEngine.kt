package com.roadsearch.openeditvideo.media

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.common.audio.GainProcessor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.model.ChromaKeySettings
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.VideoClip
import com.roadsearch.openeditvideo.model.VideoFilter
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(UnstableApi::class)
class MediaEngine(private val context: Context) {

    fun mediaItem(uri: Uri, startMs: Long = 0L, endMs: Long? = null): MediaItem {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs.coerceAtLeast(0L))
            .apply {
                if (endMs != null && endMs > startMs) setEndPositionMs(endMs)
            }
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clipping)
            .build()
    }

    fun effects(settings: com.roadsearch.openeditvideo.model.EffectSettings): List<Effect> = buildList {
        if (settings.rotation != 0f) {
            add(ScaleAndRotateTransformation.Builder().setRotationDegrees(settings.rotation).build())
        }
        if (settings.brightness != 0f) add(Brightness(settings.brightness.coerceIn(-1f, 1f)))
        if (settings.contrast != 0f) add(Contrast(settings.contrast.coerceIn(-1f, 1f)))
        if (settings.saturation != 1f) add(HslAdjustment.Builder().adjustSaturation((settings.saturation.coerceIn(0f, 2f) - 1f) * 100f).build())
        if (settings.hue != 0f) add(HslAdjustment.Builder().adjustHue(settings.hue).build())
        AdvancedEffects.color(settings.filter).let(::addAll)
        AdvancedEffects.blur(settings.blur)?.let(::add)
    }

    fun previewEffects(state: EditorUiState, clip: VideoClip): List<Effect> = buildList {
        addAll(effects(clip.effects))
        val animation = clip.animation
        if (animation.x.isNotEmpty() || animation.y.isNotEmpty() || animation.scale.isNotEmpty() || animation.rotation.isNotEmpty()) {
            add(AnimatedTransformEffect(animation, clip.startMs))
        }
        if (animation.opacity.isNotEmpty()) {
            add(AnimatedAlphaEffect(animation.opacity, clip.startMs))
        }

        val chroma = state.chromaKeys[clip.id]
        if (chroma?.enabled == true) add(chromaEffect(chroma))
        val mask = state.masks[clip.id]
        if (mask?.enabled == true) add(MaskEffect(mask))
    }

    private fun chromaEffect(settings: ChromaKeySettings): Effect {
        val a = settings.colorArgb
        return ChromaKeyEffect(
            red = Color.red(a) / 255f,
            green = Color.green(a) / 255f,
            blue = Color.blue(a) / 255f,
            threshold = settings.threshold,
            softness = settings.softness,
        )
    }

    private fun editedVideo(state: EditorUiState, clip: VideoClip): EditedMediaItem {
        val resolvedEnd = resolveEndMs(clip)
        return EditedMediaItem.Builder(mediaItem(clip.uri, clip.startMs, resolvedEnd))
            .setEffects(Effects(emptyList(), previewEffects(state, clip)))
            .build()
    }

    internal fun effectsForClipForComposition(state: EditorUiState, clip: VideoClip): Effects =
        Effects(listOf(constantGainProcessor(clip.volume.coerceIn(0f, 2f))), buildCompositionEffects(state, clip))

    private fun buildCompositionEffects(state: EditorUiState, clip: VideoClip): List<Effect> = buildList {
        val static = clip.effects.copy(rotation = 0f)
        addAll(GpuEffectFactory.build(static))
        val chroma = state.chromaKeys[clip.id]
        if (chroma?.enabled == true) add(chromaEffect(chroma))
        val mask = state.masks[clip.id]
        if (mask?.enabled == true) add(MaskEffect(mask))
    }

    companion object {
        internal fun constantGainProcessor(gain: Float): GainProcessor =
            GainProcessor(object : GainProcessor.GainProvider {
                override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float =
                    gain.coerceIn(0f, 1f)

                override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long =
                    if (gain.coerceIn(0f, 1f) == 1f) androidx.media3.common.C.TIME_END_OF_SOURCE
                    else androidx.media3.common.C.TIME_UNSET
            })
    }

    private fun resolveEndMs(clip: VideoClip): Long {
        if (clip.endMs > clip.startMs) return clip.endMs
        val sourceDuration = clip.sourceDurationMs.takeIf { it > clip.startMs }
            ?: readDurationMs(clip.uri).coerceAtLeast(TimelineMath.MIN_CLIP_DURATION_MS)
        return sourceDuration.coerceAtLeast(clip.startMs + TimelineMath.MIN_CLIP_DURATION_MS)
    }

    private fun readDurationMs(uri: Uri): Long = MediaProbe.durationMs(context, uri)

    /**
     * Export runs through Media3 Transformer. The Transformer object is created and used only on
     * the main application thread, as required by Media3. The calling worker remains suspended
     * until completion/error, and cancellation propagates to Transformer.cancel().
     */
    suspend fun exportCompositionSuspend(
        state: EditorUiState,
        output: java.io.File,
        settings: com.roadsearch.openeditvideo.export.ExportSettings = com.roadsearch.openeditvideo.export.ExportSettings(),
        onProgress: (Float) -> Unit = {},
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val mainHandler = Handler(Looper.getMainLooper())
        val holder = ProgressHolder()
        var transformer: Transformer? = null
        var pollingStarted = false

        val poller = object : Runnable {
            override fun run() {
                val current = transformer ?: return
                try {
                    when (current.getProgress(holder)) {
                        Transformer.PROGRESS_STATE_AVAILABLE -> onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                        Transformer.PROGRESS_STATE_NOT_STARTED -> Unit
                        else -> Unit
                    }
                    if (!continuation.isCompleted) mainHandler.postDelayed(this, 350L)
                } catch (_: Throwable) {
                    if (!continuation.isCompleted) mainHandler.postDelayed(this, 500L)
                }
            }
        }

        fun finish(action: () -> Unit) {
            mainHandler.removeCallbacks(poller)
            action()
        }

        continuation.invokeOnCancellation {
            mainHandler.post {
                mainHandler.removeCallbacks(poller)
                runCatching { transformer?.cancel() }
            }
        }

        mainHandler.post {
            try {
                val composition = MultiTrackCompositionFactory(context).build(state, settings)
                transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(
                                androidx.media3.transformer.VideoEncoderSettings.Builder().setBitrate(settings.videoBitrate).build(),
                            )
                            .build(),
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (!continuation.isCompleted) {
                                finish { continuation.resume(Unit) }
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            if (!continuation.isCompleted) {
                                finish { continuation.resumeWithException(exception) }
                            }
                        }
                    })
                    .build()

                transformer.start(composition, output.absolutePath)
                if (!pollingStarted) {
                    pollingStarted = true
                    mainHandler.post(poller)
                }
            } catch (t: Throwable) {
                if (!continuation.isCompleted) finish { continuation.resumeWithException(t) }
            }
        }
    }
}
