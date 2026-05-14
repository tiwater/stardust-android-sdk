package cn.ticos.stardust.sample.model

data class Speaker(
    val name: String,
    val voice: String,
    val provider: String? = null,
    val language: List<String>? = null,
    val gender: String? = null,
    val tags: List<String>? = null,
)

data class SpeakerPage(
    val speakers: List<Speaker>,
    val speakersCount: Int,
    val totalSpeakersCount: Int,
)
