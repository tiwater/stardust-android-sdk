package cn.ticos.stardust.sample.util

import java.net.URI

/**
 * 从 WebSocket URL 推导 HTTP API 根地址（用于 GET /tts 等）。
 * 例如 `wss://stardust.ticos.cn/realtime` → `https://stardust.ticos.cn`
 *
 * 使用 [URI] 解析，对非标准路径（如 `wss://custom.server.com/api/v1`）同样可以
 * 正确提取 scheme + host + port，不依赖路径名称匹配。
 */
fun deriveApiBaseUrl(serverUrl: String): String {
    val uri = runCatching { URI(serverUrl.trim()) }.getOrNull()
    if (uri != null && uri.host != null) {
        val httpScheme = if (uri.scheme == "wss") "https" else "http"
        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        return "$httpScheme://${uri.host}$portPart"
    }
    // Fallback: 简单字符串替换（理论上不会走到这里）
    return serverUrl.trim().trimEnd('/')
        .substringBefore("/realtime")
        .substringBefore("/video")
        .replace("wss://", "https://")
        .replace("ws://", "http://")
}
