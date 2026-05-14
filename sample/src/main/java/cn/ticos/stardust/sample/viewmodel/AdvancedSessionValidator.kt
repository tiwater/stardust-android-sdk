package cn.ticos.stardust.sample.viewmodel

import cn.ticos.stardust.sample.model.AdvancedSessionSettings

private const val MAX_INSTRUCTIONS_LEN = 8000

fun validateAdvancedSession(adv: AdvancedSessionSettings): String? {
    if (adv.modelProvider.isBlank()) return "Model provider is required"
    if (adv.modelName.isBlank()) return "Model name is required"
    if (adv.instructions.isBlank()) return "System instructions is required"
    if (adv.instructions.length > MAX_INSTRUCTIONS_LEN) {
        return "System instructions must be at most $MAX_INSTRUCTIONS_LEN characters"
    }
    if (adv.temperature !in 0.01..1.0) return "Temperature must be between 0.01 and 1.0"
    if (adv.topP !in 0.0..1.0) return "Top P must be between 0.0 and 1.0"
    if (adv.topK < 1) return "Top K must be a positive integer"
    if (adv.maxResponseOutputTokens < 1) return "Max tokens must be a positive integer"
    if (adv.historyConversationLength !in 0..30) return "History length must be 0-30"
    if (adv.speechVoice.isBlank()) return "Speech voice is required"
    if (adv.speechSpeedRatio !in 1..100) return "Speed ratio must be 1-100"
    if (adv.speechPitchRatio !in 1..100) return "Pitch ratio must be 1-100"
    if (adv.speechVolumeRatio !in 1..100) return "Volume ratio must be 1-100"
    if (adv.hearingPrefixPaddingMs < 0) return "Prefix padding must be non-negative"
    if (adv.hearingSilenceDurationMs < 0) return "Silence duration must be non-negative"
    if (adv.hearingSensitivity !in 0.0..1.0) return "Sensitivity must be between 0 and 1"
    return null
}
