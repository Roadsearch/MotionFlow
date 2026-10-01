package com.roadsearch.openeditvideo.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence

/** Preview controller for the same Composition model used by Transformer. */
@UnstableApi
class CompositionPreviewController(context: Context) {
    val player: CompositionPlayer = CompositionPlayer.Builder(context)
        .experimentalSetEnableReplayableCache(true)
        .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
        .build()

    fun setComposition(composition: Composition) {
        player.setComposition(composition)
        player.prepare()
    }

    fun play() = player.play()
    fun pause() = player.pause()
    fun setScrubbingMode(enabled: Boolean) = player.setScrubbingModeEnabled(enabled)
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun addListener(listener: Player.Listener) = player.addListener(listener)
    fun release() = player.release()

    companion object {
        fun simpleVideoSequence(items: List<MediaItem>): EditedMediaItemSequence {
            val edited = items.map { EditedMediaItem.Builder(it).build() }
            return EditedMediaItemSequence.withAudioAndVideoFrom(edited)
        }
    }
}
