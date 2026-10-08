package com.roadsearch.openeditvideo.model

import kotlinx.serialization.Serializable

/**
 * Invisible controller layer. Layers (and other nulls) can be parented to it; animating the null moves, scales,
 * rotates and fades all its children. [animation] is expressed in TIMELINE time (not clip-local time).
 * Units match [Keyframe]: pixels in the 1080x1920 design space, y down, degrees, scale and opacity as factors.
 */
@Serializable
data class NullObject(
    val id: Long,
    val name: String = "Null",
    val parentId: Long? = null,
    val animation: TransformAnimation = TransformAnimation(),
)
