package com.roadsearch.openeditvideo.model

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.serialization.Serializable

/** Interpolation applied from a keyframe to the next keyframe. */
@Serializable
enum class Easing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    CUBIC_BEZIER,
    HOLD,
    BOUNCE,
    ELASTIC,
    STEPS,
}

/** Independent animation lanes, matching the property-specific workflow used by professional editors. */
enum class AnimatedProperty(val label: String) {
    X("Position X"),
    Y("Position Y"),
    SCALE("Échelle"),
    ROTATION("Rotation"),
    OPACITY("Opacité"),
}

/** Value of a property when it is neither animated nor explicitly set. */
val AnimatedProperty.neutralValue: Float
    get() = when (this) {
        AnimatedProperty.SCALE, AnimatedProperty.OPACITY -> 1f
        else -> 0f
    }

/** Keeps edited values inside what the renderer can represent (the compositor clamps scale to 0.01..20). */
fun AnimatedProperty.sanitize(value: Float): Float = when (this) {
    AnimatedProperty.OPACITY -> value.coerceIn(0f, 1f)
    AnimatedProperty.SCALE -> value.coerceIn(0.01f, 20f)
    else -> value
}

@Serializable
data class AnimatedKeyframe(
    val timeMs: Long,
    val value: Float,
    val easingToNext: Easing = Easing.LINEAR,
    val curveX1: Float = 0.25f,
    val curveY1: Float = 0.1f,
    val curveX2: Float = 0.25f,
    val curveY2: Float = 1f,
)

private fun cubicBezier(a: Float, b: Float, t: Float): Float {
    val inverse = 1f - t
    return 3f * inverse * inverse * t * a +
        3f * inverse * t * t * b +
        t * t * t
}

/** Converts normalized timeline progress to eased progress. Bezier X is inverted numerically. */
fun Easing.apply(
    t0: Float,
    x1: Float = 0.25f,
    y1: Float = 0.1f,
    x2: Float = 0.25f,
    y2: Float = 1f,
): Float {
    val t = t0.coerceIn(0f, 1f)
    if (t <= 0f || t >= 1f) return t

    return when (this) {
        Easing.LINEAR -> t
        Easing.EASE_IN -> t * t
        Easing.EASE_OUT -> 1f - (1f - t).pow(2)
        Easing.EASE_IN_OUT -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f
        Easing.CUBIC_BEZIER -> {
            val bx1 = x1.coerceIn(0f, 1f)
            val bx2 = x2.coerceIn(0f, 1f)
            var low = 0f
            var high = 1f
            var parameter = t
            repeat(18) {
                parameter = (low + high) / 2f
                if (cubicBezier(bx1, bx2, parameter) < t) low = parameter else high = parameter
            }
            cubicBezier(y1.coerceIn(-2f, 3f), y2.coerceIn(-2f, 3f), parameter)
        }
        Easing.HOLD -> 0f
        Easing.BOUNCE -> bounceOut(t)
        Easing.ELASTIC -> {
            val oscillation = sin(((t * 10f - 0.75f) * (2f * Math.PI.toFloat() / 3f)).toDouble()).toFloat()
            2f.pow(-10f * t) * oscillation + 1f
        }
        Easing.STEPS -> floor(t * 8f) / 8f
    }
}

private fun bounceOut(t: Float): Float {
    val n1 = 7.5625f
    val d1 = 2.75f
    return when {
        t < 1f / d1 -> n1 * t * t
        t < 2f / d1 -> {
            val shifted = t - 1.5f / d1
            n1 * shifted * shifted + 0.75f
        }
        t < 2.5f / d1 -> {
            val shifted = t - 2.25f / d1
            n1 * shifted * shifted + 0.9375f
        }
        else -> {
            val shifted = t - 2.625f / d1
            n1 * shifted * shifted + 0.984375f
        }
    }
}

/**
 * Chronological lane with one keyframe per instant. Input may be unordered; when two keyframes share a
 * time, the one stored last wins so every consumer (preview, export, UI) sees the same lane.
 */
fun List<AnimatedKeyframe>.normalized(): List<AnimatedKeyframe> {
    if (size < 2) return this
    val result = ArrayList<AnimatedKeyframe>(size)
    for (key in sortedBy { it.timeMs }) {
        if (result.isNotEmpty() && result[result.lastIndex].timeMs == key.timeMs) result[result.lastIndex] = key else result.add(key)
    }
    return result
}

fun List<AnimatedKeyframe>.valueAt(timeMs: Long, fallback: Float): Float {
    if (isEmpty()) return fallback
    val sorted = normalized()
    if (timeMs <= sorted.first().timeMs) return sorted.first().value
    if (timeMs >= sorted.last().timeMs) return sorted.last().value
    // first.time < timeMs < last.time here, so a right neighbour exists and left.time <= timeMs < right.time.
    val rightIndex = sorted.indexOfFirst { it.timeMs > timeMs }
    val left = sorted[rightIndex - 1]
    val right = sorted[rightIndex]
    val raw = (timeMs - left.timeMs).toFloat() / (right.timeMs - left.timeMs).toFloat()
    // The curve belongs to the starting keyframe of this segment ("easing to next").
    val t = left.easingToNext.apply(raw, left.curveX1, left.curveY1, left.curveX2, left.curveY2)
    return left.value + (right.value - left.value) * t
}

/**
 * Per-property keyframe lanes plus the static value used while a lane has no keyframe.
 * A property with keyframes follows its lane; without keyframes it holds its static (base) value.
 */
@Serializable
data class TransformAnimation(
    val x: List<AnimatedKeyframe> = emptyList(),
    val y: List<AnimatedKeyframe> = emptyList(),
    val scale: List<AnimatedKeyframe> = emptyList(),
    val rotation: List<AnimatedKeyframe> = emptyList(),
    val opacity: List<AnimatedKeyframe> = emptyList(),
    val baseX: Float = 0f,
    val baseY: Float = 0f,
    val baseScale: Float = 1f,
    val baseRotation: Float = 0f,
    val baseOpacity: Float = 1f,
)

fun TransformAnimation.keyframes(property: AnimatedProperty): List<AnimatedKeyframe> = when (property) {
    AnimatedProperty.X -> x
    AnimatedProperty.Y -> y
    AnimatedProperty.SCALE -> scale
    AnimatedProperty.ROTATION -> rotation
    AnimatedProperty.OPACITY -> opacity
}

fun TransformAnimation.withKeyframes(
    property: AnimatedProperty,
    keyframes: List<AnimatedKeyframe>,
): TransformAnimation = when (property) {
    AnimatedProperty.X -> copy(x = keyframes)
    AnimatedProperty.Y -> copy(y = keyframes)
    AnimatedProperty.SCALE -> copy(scale = keyframes)
    AnimatedProperty.ROTATION -> copy(rotation = keyframes)
    AnimatedProperty.OPACITY -> copy(opacity = keyframes)
}

fun TransformAnimation.staticValue(property: AnimatedProperty): Float = when (property) {
    AnimatedProperty.X -> baseX
    AnimatedProperty.Y -> baseY
    AnimatedProperty.SCALE -> baseScale
    AnimatedProperty.ROTATION -> baseRotation
    AnimatedProperty.OPACITY -> baseOpacity
}

fun TransformAnimation.withStaticValue(property: AnimatedProperty, value: Float): TransformAnimation = when (property) {
    AnimatedProperty.X -> copy(baseX = value)
    AnimatedProperty.Y -> copy(baseY = value)
    AnimatedProperty.SCALE -> copy(baseScale = value)
    AnimatedProperty.ROTATION -> copy(baseRotation = value)
    AnimatedProperty.OPACITY -> copy(baseOpacity = value)
}

/** Value of one property at [timeMs]: its lane when keyframed, otherwise its static value. */
fun TransformAnimation.value(property: AnimatedProperty, timeMs: Long): Float =
    keyframes(property).valueAt(timeMs, staticValue(property))

fun TransformAnimation.at(timeMs: Long): Keyframe = Keyframe(
    timeMs = timeMs,
    x = x.valueAt(timeMs, baseX),
    y = y.valueAt(timeMs, baseY),
    scale = scale.valueAt(timeMs, baseScale),
    rotation = rotation.valueAt(timeMs, baseRotation),
    opacity = opacity.valueAt(timeMs, baseOpacity),
)

/** True when position, scale or rotation can differ from the untransformed layer (keyframed or statically set). */
fun TransformAnimation.hasTransform(): Boolean =
    x.isNotEmpty() || y.isNotEmpty() || scale.isNotEmpty() || rotation.isNotEmpty() ||
        baseX != 0f || baseY != 0f || baseScale != 1f || baseRotation != 0f

/** True when opacity can differ from fully opaque (keyframed or statically set). */
fun TransformAnimation.hasOpacity(): Boolean = opacity.isNotEmpty() || baseOpacity != 1f

/** True when the animation leaves the layer exactly as imported. */
fun TransformAnimation.isIdentity(): Boolean = !hasTransform() && !hasOpacity()

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
