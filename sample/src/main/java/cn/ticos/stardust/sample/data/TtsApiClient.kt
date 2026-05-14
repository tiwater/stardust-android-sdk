package cn.ticos.stardust.sample.data

import cn.ticos.stardust.sample.model.SpeakerPage
import cn.ticos.stardust.sample.util.deriveApiBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class TtsApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getSpeakers(
        serverUrl: String,
        language: String? = null,
        gender: String? = null,
        name: String? = null,
        provider: String? = null,
        tags: String? = null,
        skip: Int = 0,
        top: Int = 20,
        all: Boolean = true,
    ): Result<SpeakerPage> = withContext(Dispatchers.IO) {
        runCatching {
            val base = deriveApiBaseUrl(serverUrl).trimEnd('/')
            val ttsUrl = try {
                "$base/tts".toHttpUrl()
            } catch (t: Throwable) {
                throw IOException("Invalid TTS API URL derived from '$serverUrl': ${t.message}")
            }
            val url = ttsUrl.newBuilder().apply {
                language?.let { addQueryParameter("language", it) }
                gender?.let { addQueryParameter("gender", it) }
                name?.let { addQueryParameter("name", it) }
                provider?.let { addQueryParameter("provider", it) }
                tags?.let { addQueryParameter("tags", it) }
                addQueryParameter("skip", skip.toString())
                addQueryParameter("top", top.toString())
                if (all) addQueryParameter("all", "true")
            }.build()

            val request = Request.Builder().url(url).get().build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("TTS API error: ${response.code}")
                }

                val body = response.body?.string()
                    ?: throw IOException("Empty response body")
                val apiResponse = json.decodeFromString(TtsApiResponse.serializer(), body)

                if (apiResponse.code != 0) {
                    throw IOException("TTS API code=${apiResponse.code}: ${apiResponse.message}")
                }

                val data = apiResponse.data
                    ?: throw IOException("TTS API missing data field")
                data.toSpeakerPage()
            }
        }
    }
}
