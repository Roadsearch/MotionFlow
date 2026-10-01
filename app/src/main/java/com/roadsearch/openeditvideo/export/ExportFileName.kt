package com.roadsearch.openeditvideo.export

object ExportFileName {
    fun normalize(requested: String): String {
        val cleaned = requested
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.')
            .ifBlank { "OpenEditVideo" }
        return if (cleaned.endsWith(".mp4", ignoreCase = true)) cleaned else "$cleaned.mp4"
    }
}
