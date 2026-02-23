package io.github.kotlin.fibonacci.logic.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

// --- 2. Serialization Data Models for Gemini 3 API ---
@Serializable
data class GeminiRequest(
    val systemInstruction: Content? = null,
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class GenerationConfig(
    val thinkingConfig: ThinkingConfig? = null,
    val temperature: Double? = null
)

@Serializable
data class ThinkingConfig(
    val thinkingLevel: String
)

@Serializable
data class Content(
    val parts: List<Part>,
    val role: String? = null
)

@Serializable
data class Part(
    val text: String? = null,
    val thoughtSignature: String? = null // Crucial for Gemini 3 reasoning context
)

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content
)

// --- 3. The Client Implementation ---
class GeminiAiClient(
    private val apiKey: String,
    private val httpClient: HttpClient
) : AiClient {

    // Target the new Gemini 3 Flash Preview model
    private val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent"

    // Store the latest signature. Initialized with the bypass string from the migration docs!
    private var latestThoughtSignature: String = "context_engineering_is_the_way_to_go"

    override suspend fun generateNarrative(systemPrompt: String, userInput: String): String {
        val requestBody = GeminiRequest(
            systemInstruction = Content(
                parts = listOf(Part(text = systemPrompt))
            ),
            contents = listOf(
                Content(
                    role = "user",
                    // Inject the required thought signature to maintain reasoning capabilities
                    parts = listOf(Part(text = userInput, thoughtSignature = latestThoughtSignature))
                )
            ),
            generationConfig = GenerationConfig(
                // "low" minimizes latency for simple instruction following
                thinkingConfig = ThinkingConfig(thinkingLevel = "low"),
                // Gemini 3 relies on temperature 1.0 for optimal reasoning
                temperature = 1.0
            )
        )

        return try {
            val response: HttpResponse = httpClient.post(endpoint) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("x-goog-api-key", apiKey)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val geminiResponse = response.body<GeminiResponse>()
                val responseContent = geminiResponse.candidates?.firstOrNull()?.content
                val responsePart = responseContent?.parts?.firstOrNull()

                // Capture the model's new thought signature for the next turn
                responsePart?.thoughtSignature?.let {
                    latestThoughtSignature = it
                }

                responsePart?.text ?: "Engine Error: Gemini returned an empty successful response."
            } else {
                "HTTP Error: ${response.status.value} - ${response.bodyAsText()}"
            }
        } catch (e: Exception) {
            "Network Error: Failed to reach Gemini API. ${e.message}"
        }
    }
}