package com.roadsearch.openeditvideo.model

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.serialization.Serializable

/** Interpolation used by a property segment. */
@Serializable
enum class Easing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

@Serializable
data class AnimatedKeyframe(
    val timeMs: Long,
    val value: Float,
    val easingToNext: Easing = Easing.LINEAR
)

fun Easing.apply(t0: Float): Float {
    val t = t0.coerceIn(0f, 1f)
    return when (this) {
        Easing.LINEAR -> t
        Easing.EASE_IN -> t * t
        Easing.EASE_OUT -> 1f - (1f - t).pow(2)
        Easing.EASE_IN_OUT -> if (t < .5f) 2f*t*t else 1f - (-2f*t+2f).pow(2)/2f
    }
}

fun List<AnimatedKeyframe>.valueAt(timeMs: Long, fallback: Float): Float {
    if (isEmpty()) return fallback
    val sorted = sortedBy { it.timeMs }
    if (timeMs <= sorted.first().timeMs) return sorted.first().value
    if (timeMs >= sorted.last().timeMs) return sorted.last().value
    val right = sorted.first { it.timeMs >= timeMs }
    val left = sorted.last { it.timeMs <= timeMs }
    if (right.timeMs == left.timeMs) return left.value
    val raw = (timeMs-left.timeMs).toFloat()/(right.timeMs-left.timeMs).toFloat()
    val t = right.easingToNext.apply(raw)
    return left.value + (right.value-left.value)*t
}

@Serializable
data class TransformAnimation(
    val x: List<AnimatedKeyframe> = emptyList(),
    val y: List<AnimatedKeyframe> = emptyList(),
    val scale: List<AnimatedKeyframe> = emptyList(),
    val rotation: List<AnimatedKeyframe> = emptyList(),
    val opacity: List<AnimatedKeyframe> = emptyList(),
)

fun TransformAnimation.at(timeMs: Long): Keyframe = Keyframe(
    timeMs = timeMs,
    x = x.valueAt(timeMs, 0f),
    y = y.valueAt(timeMs, 0f),
    scale = scale.valueAt(timeMs, 1f),
    rotation = rotation.valueAt(timeMs, 0f),
    opacity = opacity.valueAt(timeMs, 1f),
)

@Serializable
data class Transition(
    val id: Long,
    val fromClipId: Long,
    val toClipId: Long,
    val durationMs: Long = 500L,
    val type: TransitionType = TransitionType.CROSS_FADE,
)

@Serializable
enum class TransitionType { CUT, CROSS_FADE, FADE_THROUGH, WIPE_LEFT, WIPE_RIGHT }

fun Transition.progress(timeMs: Long, boundaryMs: Long): Float =
    ((timeMs - (boundaryMs - durationMs / 2)).toFloat() / durationMs).coerceIn(0f, 1f)
