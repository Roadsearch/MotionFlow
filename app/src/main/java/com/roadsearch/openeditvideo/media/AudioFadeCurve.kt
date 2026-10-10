package com.roadsearch.openeditvideo.media

/**
 * Linear gain envelope for a timeline audio clip. Sample positions are relative to the clipped
 * media item. A fade never amplifies beyond the requested clip volume.
 */
internal object AudioFadeCurve {
    fun gainAt(
        samplePosition: Long,
        sampleRate: Int,
        durationMs: Long,
        volume: Float,
        fadeInMs: Long,
        fadeOutMs: Long,
    ): Float {
        if (sampleRate <= 0 || durationMs <= 0L) return 0f
        val durationSamples = durationMs * sampleRate / 1000L
        val position = samplePosition.coerceIn(0L, durationSamples)
        val fadeInSamples = fadeInMs.coerceAtLeast(0L) * sampleRate / 1000L
        val fadeOutSamples = fadeOutMs.coerceAtLeast(0L) * sampleRate / 1000L
        var envelope = 1f
        if (fadeInSamples > 0L) envelope = minOf(envelope, (position.toFloat() / fadeInSamples).coerceIn(0f, 1f))
        if (fadeOutSamples > 0L) {
            val remaining = (durationSamples - position).coerceAtLeast(0L)
            envelope = minOf(envelope, (remaining.toFloat() / fadeOutSamples).coerceIn(0f, 1f))
        }
        return envelope * volume.coerceIn(0f, 2f)
    }
}
