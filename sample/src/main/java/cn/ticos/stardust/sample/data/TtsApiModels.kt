package cn.ticos.stardust.sample.data

import cn.ticos.stardust.sample.model.Speaker
import cn.ticos.stardust.sample.model.SpeakerPage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TtsApiResponse(
    val code: Int,
    val message: String,
    val data: TtsApiData? = null,
)

@Serializable
data class TtsApiData(
    val speakers: List<TtsSpeakerDto> = emptyList(),
    @SerialName("speakers_count") val speakersCount: Int = 0,
    @SerialName("total_speakers_count") val totalSpeakersCount: Int = 0,
)

@Serializable
data class TtsSpeakerDto(
    val name: String,
    val voice: String,
    val provider: String? = null,
    val language: List<String>? = null,
    val gender: String? = null,
    val tags: List<String>? = null,
)

fun TtsApiData.toSpeakerPage(): SpeakerPage = SpeakerPage(
    speakers = speakers.map { dto ->
        Speaker(
            name = dto.name,
            voice = dto.voice,
            provider = dto.provider,
            language = dto.language,
            gender = dto.gender,
            tags = dto.tags,
        )
    },
    speakersCount = speakersCount,
    totalSpeakersCount = totalSpeakersCount,
)
