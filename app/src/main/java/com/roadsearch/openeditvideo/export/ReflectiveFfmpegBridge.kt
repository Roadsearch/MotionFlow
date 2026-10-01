package com.roadsearch.openeditvideo.export

import java.lang.reflect.Method

/**
 * Optional runtime bridge: no hard FFmpeg dependency is required by the default build.
 * If the maintained FFmpegKit fork is bundled by the app, this bridge can execute a command
 * without coupling the core editor to its Java API. The maintained fork intentionally preserves
 * the com.arthenica.ffmpegkit package for source compatibility.
 */
class ReflectiveFfmpegBridge {
    fun isAvailable(): Boolean = runCatching {
        Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
        Class.forName("com.arthenica.ffmpegkit.ReturnCode")
        true
    }.getOrDefault(false)

    fun execute(command: List<String>): Boolean {
        val ffmpegClass = Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
        val session = ffmpegClass.getMethod("execute", String::class.java).invoke(null, shellQuote(command))
        val returnCode = session.javaClass.getMethod("getReturnCode").invoke(session)
        val returnCodeClass = Class.forName("com.arthenica.ffmpegkit.ReturnCode")
        val isSuccess: Method = returnCodeClass.getMethod("isSuccess", returnCodeClass)
        return isSuccess.invoke(null, returnCode) as? Boolean ?: false
    }

    private fun shellQuote(arguments: List<String>): String = arguments.joinToString(" ") { arg ->
        "'" + arg.replace("'", "'\\''") + "'"
    }
}
