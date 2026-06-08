package app.lawnchair.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@Serializable
data class DeepSeekMessage(
    val role: String,
    val content: String,
)

@Serializable
data class DeepSeekRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<DeepSeekMessage>,
    val temperature: Double = 0.0,
)

@Serializable
data class DeepSeekChoice(
    val message: DeepSeekMessage,
)

@Serializable
data class DeepSeekResponse(
    val choices: List<DeepSeekChoice>,
)

interface DeepSeekApiService {

    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: DeepSeekRequest,
    ): DeepSeekResponse

    companion object {
        private const val BASE_URL = "https://api.deepseek.com/"
        const val CHAT_MODEL = "deepseek-chat"

        private val json = Json { ignoreUnknownKeys = true }

        private val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        private val retrofit: Retrofit by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(
                    json.asConverterFactory("application/json; charset=UTF8".toMediaType()),
                )
                .build()
        }

        fun create(): DeepSeekApiService = retrofit.create(DeepSeekApiService::class.java)
    }
}
