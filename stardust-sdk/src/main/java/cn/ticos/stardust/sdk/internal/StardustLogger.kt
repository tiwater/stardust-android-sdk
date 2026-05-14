package cn.ticos.stardust.sdk.internal

import android.util.Log
import cn.ticos.stardust.sdk.StardustLogLevel

internal class StardustLogger(
    private val level: StardustLogLevel,
    private val tag: String = "StardustSdk",
) {
    fun d(msg: String) = log(StardustLogLevel.DEBUG, msg)
    fun i(msg: String) = log(StardustLogLevel.INFO, msg)
    fun w(msg: String) = log(StardustLogLevel.WARN, msg)
    fun e(msg: String, tr: Throwable? = null) {
        if (!enabled(StardustLogLevel.ERROR)) return
        if (tr == null) Log.e(tag, msg) else Log.e(tag, msg, tr)
    }

    fun redactJson(json: String): String {
        val keys = listOf(
            "authorization",
            "token",
            "terminal_secret",
            "api_key",
            "mcp_api_key",
            "audio",
            "delta",
            "image_url",
            "video",
        )
        var output = json
        keys.forEach { key ->
            output = output.replace(Regex("\"$key\"\\s*:\\s*\"[^\"]*\""), "\"$key\":\"***\"")
        }
        return output
    }

    private fun log(target: StardustLogLevel, msg: String) {
        if (!enabled(target)) return
        when (target) {
            StardustLogLevel.DEBUG -> Log.d(tag, msg)
            StardustLogLevel.INFO -> Log.i(tag, msg)
            StardustLogLevel.WARN -> Log.w(tag, msg)
            StardustLogLevel.ERROR -> Log.e(tag, msg)
            StardustLogLevel.NONE -> Unit
        }
    }

    private fun enabled(target: StardustLogLevel): Boolean {
        return when (level) {
            StardustLogLevel.NONE -> false
            StardustLogLevel.ERROR -> target == StardustLogLevel.ERROR
            StardustLogLevel.WARN -> target == StardustLogLevel.ERROR || target == StardustLogLevel.WARN
            StardustLogLevel.INFO -> target != StardustLogLevel.DEBUG && target != StardustLogLevel.NONE
            StardustLogLevel.DEBUG -> target != StardustLogLevel.NONE
        }
    }
}
