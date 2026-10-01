package com.roadsearch.openeditvideo.model

import kotlinx.serialization.Serializable

/** A legacy all-properties keyframe kept for backwards-compatible project documents. */
@Serializable
data class Keyframe(
    val timeMs: Long,
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
)
