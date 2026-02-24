package io.github.kotlin.fibonacci.logic.ai

import io.github.kotlin.fibonacci.domain.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// --- 1. Serialization Data Models for Gemini 3 API ---
@Serializable
data class GeminiRequest(
    val systemInstruction: Content? = null,
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class GenerationConfig(
    val thinkingConfig: ThinkingConfig? = null,
    val temperature: Double? = null,
    val responseMimeType: String? = null
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
    val thoughtSignature: String? = null
)

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content
)

// --- 2. DTOs for parsing the Director's JSON Output ---
@Serializable
data class DirectorPlaybookDto(
    val intentJson: IntentDto,
    val characterCues: List<CharacterCueDto>,
    val narrativeNotes: String
)

@Serializable
data class IntentDto(
    val type: String,
    val destinationName: String? = null,
    val target: String? = null,
    val description: String? = null,
    val hoursTaken: Int = 0
)

@Serializable
data class CharacterCueDto(
    val characterName: String,
    val emotionalState: String,
    val directive: String
)

// --- 3. The Client Implementation ---
class GeminiAiClient(
    private val apiKey: String,
    private val httpClient: HttpClient
) : AiClient {

    private val proEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro-preview:generateContent"
    private val flashEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent"

    private val jsonParser = Json { ignoreUnknownKeys = true }

    // --- TURN 0: THE PROLOGUE ---
    override suspend fun generatePrologue(
        worldState: WorldState,
        mainCharacter: Character,
        context: String
    ): String {
        val systemPrompt = """
            You are the narrator of a text-based fantasy RPG. This is the opening scene.
            Time: ${worldState.currentHour}:00 | Mist: ${worldState.mistState}
            Starting Location: ${mainCharacter.currentLocation.name}
            
            WORLD & CHARACTER CONTEXT:
            $context
            
            Write the opening paragraph of the story. 
            Establish the atmosphere of the starting location and introduce the main character (${mainCharacter.name}) in their current state.
            End the paragraph with a subtle hook or observation that invites the player to take their first action.
            Do NOT make the character take significant actions, speak, or leave the location. Just set the stage.
        """.trimIndent()

        // We pass a dummy user input just to trigger the generation
        return makeGeminiCall(flashEndpoint, systemPrompt, "Begin the story.", thinkingLevel = "low")
    }

    // --- STAGE 1: THE DIRECTOR (PRO) ---
    override suspend fun getDirectorPlaybook(
        userInput: String,
        worldState: WorldState,
        mainCharacter: Character,
        globalTruth: String,
        storySoFar: String
    ): DirectorPlaybook {
        val systemPrompt = """
            You are the Director of a strict text-based RPG engine.
            Analyze the user's input and current state. 
            Time: ${worldState.currentHour}:00 | Location: ${mainCharacter.currentLocation.name}
            
            $globalTruth
            
            PREVIOUS STORY CONTEXT:
            $storySoFar
            
            Determine the mechanical intent and provide cues for any involved characters.
            Return ONLY a JSON object matching this exact structure:
            {
              "intentJson": {
                "type": "Travel" | "Action" | "Scan" | "Unknown",
                "destinationName": "string or null",
                "target": "string or null",
                "description": "string or null",
                "hoursTaken": integer (estimate time cost)
              },
              "characterCues": [
                {
                  "characterName": "string",
                  "emotionalState": "string",
                  "directive": "string (what should they do/say?)"
                }
              ],
              "narrativeNotes": "String noting any global facts (e.g., 'Ain is using rimefrost blood.')"
            }
        """.trimIndent()

        val responseText = makeGeminiCall(proEndpoint, systemPrompt, userInput, responseMimeType = "application/json", thinkingLevel = "high")

        return try {
            val dto = jsonParser.decodeFromString<DirectorPlaybookDto>(responseText)

            val domainIntent = when (dto.intentJson.type) {
                "Travel" -> Intent.Travel(dto.intentJson.destinationName ?: "Unknown")
                "Action" -> Intent.Action(dto.intentJson.description ?: "Takes action", dto.intentJson.hoursTaken)
                "Scan" -> Intent.Scan(dto.intentJson.target ?: "Surroundings")
                else -> Intent.Unknown
            }

            val domainCues = dto.characterCues.map {
                CharacterCue(it.characterName, it.emotionalState, it.directive)
            }

            DirectorPlaybook(domainIntent, domainCues, dto.narrativeNotes)
        } catch (e: Exception) {
            println("JSON Parse Error: ${e.message}. Raw: $responseText")
            DirectorPlaybook(Intent.Unknown, emptyList(), "Director encountered an error parsing intent.")
        }
    }

    // --- STAGE 2: THE ACTOR (FLASH) ---
    override suspend fun generateActorPerformance(
        characterName: String,
        context: String,
        userInput: String,
        storySoFar: String
    ): String {
        val systemPrompt = """
            You are playing the role of $characterName.
            $context
            
            PREVIOUS STORY CONTEXT:
            $storySoFar
            
            Based on the user's input and recent events, generate a very brief, in-character reaction and 1-2 lines of dialogue.
            Do not narrate for the player. Just act as $characterName.
        """.trimIndent()

        return makeGeminiCall(flashEndpoint, systemPrompt, userInput, thinkingLevel = "low")
    }

    // --- STAGE 3: THE COMPOSITOR (FLASH) ---
    override suspend fun composeFinalNarrative(
        userInput: String,
        performances: List<String>,
        systemNotes: String,
        worldState: WorldState,
        storySoFar: String
    ): String {
        val performanceText = performances.joinToString("\n") { "Actor Performance: $it" }

        val systemPrompt = """
            You are the narrator of a fantasy story.
            Time: ${worldState.currentHour}:00 | Mist: ${worldState.mistState}
            Engine Notes: $systemNotes
            
            PREVIOUS STORY CONTEXT:
            $storySoFar
            
            ACTOR PERFORMANCES FOR THIS TURN:
            $performanceText
            
            Combine the player's input and the actor performances into the NEXT paragraph of the story. 
            Ensure it flows naturally from the PREVIOUS STORY CONTEXT. Maintain a consistent fantasy tone.
        """.trimIndent()

        return makeGeminiCall(flashEndpoint, systemPrompt, userInput, thinkingLevel = "low")
    }

    // --- HELPER: GENERIC API CALL ---
    private suspend fun makeGeminiCall(
        endpoint: String,
        systemPrompt: String,
        userInput: String,
        responseMimeType: String? = null,
        thinkingLevel: String
    ): String {
        val requestBody = GeminiRequest(
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            contents = listOf(
                Content(role = "user", parts = listOf(Part(text = userInput, thoughtSignature = "context_engineering_is_the_way_to_go")))
            ),
            generationConfig = GenerationConfig(
                thinkingConfig = ThinkingConfig(thinkingLevel = thinkingLevel),
                temperature = 1.0,
                responseMimeType = responseMimeType
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
                val rawText = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                rawText.removePrefix("```json\n").removeSuffix("\n```").trim()
            } else {
                "HTTP Error: ${response.status.value}"
            }
        } catch (e: Exception) {
            "Network Error: ${e.message}"
        }
    }
}