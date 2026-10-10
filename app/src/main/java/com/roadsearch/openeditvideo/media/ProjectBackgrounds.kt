package com.roadsearch.openeditvideo.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import com.roadsearch.openeditvideo.model.AspectRatio
import java.io.File

/**
 * Ready-made backgrounds a new project can start from (the "fond" of Alight Motion): solid colours and vertical
 * gradients. A background is rendered once to a PNG and then behaves exactly like an imported photo.
 */
object ProjectBackgrounds {
    class Preset(val id: String, val label: String, val colors: List<Int>) {
        init { require(colors.isNotEmpty()) { "A background needs at least one colour" } }
        val isGradient: Boolean get() = colors.size > 1
    }

    val presets: List<Preset> = listOf(
        Preset("black", "Noir", listOf(0xFF000000.toInt())),
        Preset("white", "Blanc", listOf(0xFFFFFFFF.toInt())),
        Preset("graphite", "Graphite", listOf(0xFF1E1E2D.toInt())),
        Preset("violet", "Violet", listOf(0xFF7B2FF7.toInt())),
        Preset("ocean", "Océan", listOf(0xFF0B3D91.toInt(), 0xFF00CEFD.toInt())),
        Preset("sunset", "Couchant", listOf(0xFFFF512F.toInt(), 0xFFF09819.toInt())),
        Preset("aurora", "Aurore", listOf(0xFF9F3CF9.toInt(), 0xFF00CEFD.toInt())),
        Preset("night", "Nuit", listOf(0xFF0F0C29.toInt(), 0xFF302B63.toInt(), 0xFF24243E.toInt())),
        Preset("forest", "Forêt", listOf(0xFF134E5E.toInt(), 0xFF71B280.toInt())),
        Preset("rose", "Rose", listOf(0xFFFF758C.toInt(), 0xFFFF7EB3.toInt())),
        Preset("gold", "Or", listOf(0xFF4B3A07.toInt(), 0xFFFFC857.toInt())),
        Preset("mint", "Menthe", listOf(0xFF00B09B.toInt(), 0xFF96C93D.toInt())),
    )

    /** Pixel size of the rendered image (1080p class on the long edge), exactly the ratio of [aspect]. */
    fun sizeFor(aspect: AspectRatio): Pair<Int, Int> = when (aspect) {
        AspectRatio.PORTRAIT -> 1080 to 1920
        AspectRatio.LANDSCAPE -> 1920 to 1080
        AspectRatio.SQUARE -> 1080 to 1080
    }

    /** Renders (once) and returns the PNG for [preset] at [aspect]. Call off the main thread. */
    fun uriFor(context: Context, preset: Preset, aspect: AspectRatio): Uri {
        val (w, h) = sizeFor(aspect)
        val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }
        val file = File(dir, "bg_${preset.id}_${w}x$h.png")
        if (!file.exists() || file.length() == 0L) {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                if (preset.isGradient) {
                    paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), preset.colors.toIntArray(), null, Shader.TileMode.CLAMP)
                } else {
                    paint.color = preset.colors.first()
                }
                Canvas(bitmap).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                val tmp = File(dir, file.name + ".tmp")
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                tmp.renameTo(file)
            } finally {
                bitmap.recycle()
            }
        }
        return Uri.fromFile(file)
    }
}
