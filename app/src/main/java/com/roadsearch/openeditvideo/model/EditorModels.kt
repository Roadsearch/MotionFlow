package com.roadsearch.openeditvideo.model

import android.net.Uri
import kotlinx.serialization.Serializable
import com.roadsearch.openeditvideo.data.UriAsStringSerializer

@Serializable
data class VideoClip(
    val id: Long,
    @Serializable(with = UriAsStringSerializer::class) val uri: Uri,
    val name: String,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val sourceDurationMs: Long = 0L,
    val volume: Float = 1f,
    val track: Int = 0,
    val timelineStartMs: Long = 0L,
    val keyframes: List<Keyframe> = emptyList(),
    val animation: TransformAnimation = TransformAnimation(),
    val effects: EffectSettings = EffectSettings(),
)

@Serializable
data class AudioClip(
    val id: Long,
    @Serializable(with = UriAsStringSerializer::class) val uri: Uri,
    val name: String,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val sourceDurationMs: Long = 0L,
    val volume: Float = 1f,
    val timelineStartMs: Long = 0L,
)

@Serializable
data class TextOverlay(
    val id: Long,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val style: TextStyleSpec = TextStyleSpec(),
)

@Serializable
enum class TextPreset { CLASSIC, NEON, SCRIPT, BOLD3D }

/** Visual style of a text overlay. [font]: bebas | inter | sans | serif | cursive. [size] in px of the rendered overlay; [posY] -1 (bottom) .. 1 (top). */
@Serializable
data class TextStyleSpec(
    val preset: TextPreset = TextPreset.CLASSIC,
    val font: String = "sans",
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val size: Float = 64f,
    val posY: Float = 0f,
)

@Serializable
data class EffectSettings(
    val rotation: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 1f,
    val brightness: Float = 0f,
    val hue: Float = 0f,
    val blur: Float = 0f,
    val filter: VideoFilter = VideoFilter.NONE,
)

@Serializable
enum class VideoFilter { NONE, CINEMATIC, VINTAGE, COOL, WARM, NOIR }

@Serializable
enum class BlendMode { NORMAL, ADD, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN }

@Serializable
data class MaskSettings(
    val enabled: Boolean = false,
    val type: MaskType = MaskType.RECTANGLE,
    val feather: Float = 0f,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
    val invert: Boolean = false,
)

@Serializable
enum class MaskType { RECTANGLE, CIRCLE, LINEAR_GRADIENT, RADIAL_GRADIENT, ELLIPSE }

@Serializable
data class ChromaKeySettings(
    val enabled: Boolean = false,
    val colorArgb: Int = 0xFF00FF00.toInt(),
    val threshold: Float = 0.18f,
    val softness: Float = 0.08f,
)

/** A named point of interest on the timeline (chapters, beats, notes). */
@Serializable
data class Marker(
    val id: Long,
    val positionMs: Long,
    val label: String = "",
    val colorArgb: Int = 0xFFFFB74D.toInt(),
)

/** Per-video-track flags. Locked tracks reject edits, muted tracks are silent, hidden tracks are not composited. */
@Serializable
data class TrackState(
    val locked: Boolean = false,
    val muted: Boolean = false,
    val hidden: Boolean = false,
)


@Serializable
data class EditorUiState(
    val clips: List<VideoClip> = emptyList(),
    val audioClips: List<AudioClip> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val selectedClipId: Long? = null,
    val selectedClipIds: Set<Long> = emptySet(),
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val zoom: Float = 1f,
    val muted: Boolean = false,
    val activeTool: Tool = Tool.MEDIA,
    val effects: EffectSettings = EffectSettings(),
    val exportProgress: Float? = null,
    val exportMessage: String? = null,
    val seekNonce: Long = 0L,
    val transitions: List<Transition> = emptyList(),
    val easing: Easing = Easing.LINEAR,
    val masks: Map<Long, MaskSettings> = emptyMap(),
    val blendModes: Map<Long, BlendMode> = emptyMap(),
    val chromaKeys: Map<Long, ChromaKeySettings> = emptyMap(),
    val markers: List<Marker> = emptyList(),
    val snappingEnabled: Boolean = true,
    val trackStates: Map<Int, TrackState> = emptyMap(),
)

@Serializable
enum class Tool(val label: String) {
    MEDIA("Média"), AUDIO("Audio"), TEXT("Texte"), EFFECTS("Effets"), ADJUST("Réglages"), MORE("Plus")
}

fun EditorUiState.selectedClip(): VideoClip? = clips.firstOrNull { it.id == selectedClipId }
fun VideoClip.end(durationMs: Long): Long = if (endMs > startMs) endMs else sourceDurationMs.takeIf { it > startMs } ?: durationMs


fun List<Keyframe>.interpolate(timeMs: Long): Keyframe {
    if (isEmpty()) return Keyframe(timeMs)
    val sorted = sortedBy { it.timeMs }
    if (timeMs <= sorted.first().timeMs) return sorted.first()
    if (timeMs >= sorted.last().timeMs) return sorted.last()
    val right = sorted.first { it.timeMs >= timeMs }
    val left = sorted.last { it.timeMs <= timeMs }
    if (right.timeMs == left.timeMs) return left
    val t = (timeMs - left.timeMs).toFloat() / (right.timeMs - left.timeMs).toFloat()
    fun lerp(a: Float,b: Float)=a+(b-a)*t
    return Keyframe(timeMs, lerp(left.x,right.x), lerp(left.y,right.y), lerp(left.scale,right.scale), lerp(left.rotation,right.rotation), lerp(left.opacity,right.opacity))
}

fun VideoClip.keyframesAt(timelinePositionMs: Long): Keyframe {
    val local = (timelinePositionMs - timelineStartMs + startMs).coerceAtLeast(startMs)
    return if (animation.x.isNotEmpty() || animation.y.isNotEmpty() || animation.scale.isNotEmpty() || animation.rotation.isNotEmpty() || animation.opacity.isNotEmpty()) animation.at(local) else keyframes.interpolate(local)
}
