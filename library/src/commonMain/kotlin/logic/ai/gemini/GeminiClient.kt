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

class GeminiAiClient(
    private val apiKey: String,
    private val httpClient: HttpClient
) : AiClient {

    private val proEndpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro-preview:generateContent"
    private val flashEndpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent"

    private val jsonParser = Json { ignoreUnknownKeys = true }

    // --- TURN 0: THE PROLOGUE ---
    override suspend fun generatePrologue(
        worldState: WorldState,
        mainCharacter: Character,
        context: String,
        worldConfig: WorldConfig // INJECTED THEME
    ): String {
        val systemPrompt = """
            You are the narrator of an interactive story. This is the opening scene.
            
            STORY CONFIGURATION:
            Genre: ${worldConfig.genre}
            Tone: ${worldConfig.tone}
            Narrator Style: ${worldConfig.narratorStyle}
            
            Time: ${worldState.currentHour}:00 | Atmospheric Condition: ${worldState.mistState}
            Starting Location: ${mainCharacter.currentLocation.name}
            
            WORLD & CHARACTER CONTEXT:
            $context
            
            Write the gripping opening paragraph of the story. 
            Establish the atmosphere of the starting location and introduce the protagonist (${mainCharacter.name}).
            CRITICAL INSTRUCTION: You MUST end the prologue with an INCITING INCIDENT. An NPC should interrupt them, a threat should appear, or an urgent event must occur that forces the player to react immediately. Do not just describe the room; start the action.
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
            You are the hidden Game Master and logic engine driving an interactive story.
            
            WORLD EXPANSION RULES:
            1. If the user explores a logical sub-area not currently registered, YOU MAY INVENT IT.
            2. If the plot demands a minor NPC, YOU MAY INVENT THEM.
            3. BOUNDED CONSISTENCY: Keep all inventions strictly tied to the current location's theme and the established world context.
            
            WORLD DYNAMICS (CRITICAL):
            The world is ALIVE. NPCs do not just wait to be spoken to. If the player is in a location with other characters, or if the plot demands it, you MUST use `characterCues` to make NPCs interrupt, speak, or act independently. Drive the plot forward!
            
            Analyze the user's input and current state. 
            Time: ${worldState.timeString} | Location: ${mainCharacter.currentLocation.name} (ID: ${mainCharacter.currentLocation.id})
            
            $globalTruth
            
            PREVIOUS STORY CONTEXT:
            $storySoFar
            
            Return ONLY a JSON object matching this exact structure:
            {
              "intentJson": {
                "type": "Travel" | "Action" | "Scan" | "Unknown",
                "destinationName": "string or null",
                "target": "string or null",
                "description": "string or null",
                "timeCostMinutes": integer
              },
              "characterCues": [
                {
                  "characterName": "string",
                  "emotionalState": "string",
                  "directive": "string"
                }
              ],
              "narrativeNotes": "String noting any global facts",
              "newlyDiscoveredLocations": [
                {
                  "id": "snake_case_id",
                  "name": "string",
                  "description": "Rich lore description",
                  "connectedToLocationId": "ID of the room they just came from"
                }
              ],
              "newlyIntroducedCharacters": [
                {
                  "name": "string",
                  "locationId": "snake_case_id",
                  "background": "string",
                  "personality": "string"
                }
              ],
                "stateUpdates": {
                  "statChanges": { "StatName": "NewValue" },
                  "walletChanges": { "CurrencyName": -5 },
                  "relationshipChanges": { "NPCName": "New Status" },
                  "inventoryGained": ["Item 1"],
                  "inventoryLost": ["Item 2"]
                },
              "requestedImagePrompt": "string or null"
            }
        """.trimIndent()

        val responseText = makeGeminiCall(proEndpoint, systemPrompt, userInput, responseMimeType = "application/json", thinkingLevel = "high")

        return try {
            val dto = jsonParser.decodeFromString<DirectorPlaybookDto>(responseText)

            // 1. Map Intent
            val domainIntent = when (dto.intentJson.type) {
                "Travel" -> Intent.Travel(dto.intentJson.destinationName ?: "Unknown")
                "Action" -> Intent.Action(dto.intentJson.description ?: "Takes action", dto.intentJson.timeCostMinutes)
                "Scan" -> Intent.Scan(dto.intentJson.target ?: "Surroundings")
                else -> Intent.Unknown
            }

            // 2. Map Entities
            val domainCues = dto.characterCues.map { CharacterCue(it.characterName, it.emotionalState, it.directive) }
            val newLocs = dto.newlyDiscoveredLocations.map { DiscoveredLocation(it.id, it.name, it.description, it.connectedToLocationId) }
            val newChars = dto.newlyIntroducedCharacters.map { DiscoveredCharacter(it.name, it.locationId, it.background, it.personality) }

            // 3. Map State Updates
            val domainStateUpdates = dto.stateUpdates?.let { updatesDto ->
                StateUpdate(
                    statChanges = updatesDto.statChanges,
                    walletChanges = updatesDto.walletChanges,
                    relationshipChanges = updatesDto.relationshipChanges,
                    inventoryGained = updatesDto.inventoryGained, // NEW
                    inventoryLost = updatesDto.inventoryLost // NEW
                )
            }

            // 4. Return fully populated Playbook
            DirectorPlaybook(
                intent = domainIntent,
                characterCues = domainCues,
                narrativeNotes = dto.narrativeNotes,
                newlyDiscoveredLocations = newLocs,
                newlyIntroducedCharacters = newChars,
                stateUpdates = domainStateUpdates,
                requestedImagePrompt = dto.requestedImagePrompt
            )
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
        storySoFar: String,
        worldConfig: WorldConfig // INJECTED THEME
    ): String {
        val performanceText = performances.joinToString("\n") { "Actor Performance: $it" }

        val systemPrompt = """
            You are the narrator of an interactive story.
            
            STORY CONFIGURATION:
            Genre: ${worldConfig.genre}
            Tone: ${worldConfig.tone}
            Narrator Style: ${worldConfig.narratorStyle}

            Time: ${worldState.currentHour}:00 | Atmospheric Condition: ${worldState.mistState}
            Engine Notes: $systemNotes
            
            PREVIOUS STORY CONTEXT:
            $storySoFar
            
            ACTOR PERFORMANCES FOR THIS TURN:
            $performanceText
            
            Combine the player's input and the actor performances into the NEXT paragraph of the story. 
            Ensure it flows naturally from the PREVIOUS STORY CONTEXT. Strictly adhere to the configured Genre and Tone.
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
                Content(
                    role = "user",
                    parts = listOf(Part(text = userInput, thoughtSignature = "context_engineering_is_the_way_to_go"))
                )
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