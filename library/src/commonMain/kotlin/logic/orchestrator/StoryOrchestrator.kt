package io.github.kotlin.fibonacci.logic.orchestrator

import io.github.kotlin.fibonacci.domain.models.*
import io.github.kotlin.fibonacci.logic.context.ContextPruner
import io.github.kotlin.fibonacci.logic.managers.*
import io.github.kotlin.fibonacci.logic.ai.AiClient // <-- 1. Import the interface

class StoryOrchestrator(
    private val spatialManager: SpatialManager,
    private val chronosManager: ChronosManager,
    private val eventGenerator: EventGenerator,
    private val worldState: WorldState,
    private val mainCharacter: Character,
    private val contextPruner: ContextPruner,
    private val aiClient: AiClient // <-- 2. Inject the AI Client
) {

    private fun fetchIntent(rawInput: String): Intent {
        val lowerInput = rawInput.lowercase()
        return when {
            lowerInput.contains("run to") || lowerInput.contains("go to") || lowerInput.contains("runs to") -> {
                val dest = if (lowerInput.contains("village")) "Village" else "Unknown"
                Intent.Travel(dest)
            }
            lowerInput.contains("looks around") -> Intent.Scan("Surroundings")
            lowerInput.contains("spend the night") -> Intent.Action(rawInput, hoursTaken = 8)
            lowerInput.contains("finishes breakfast") -> Intent.Action(rawInput, hoursTaken = 1)
            else -> Intent.Unknown
        }
    }

    // 3. Change to a suspend function so we can make network calls
    suspend fun processTurn(rawInput: String): String {
        val intent = fetchIntent(rawInput)
        var systemPromptNotes = ""

        when (intent) {
            is Intent.Travel -> {
                val destination = spatialManager.getLocation(intent.destinationName)
                val startLocation = mainCharacter.currentLocation // Store the starting point

                if (destination != null) {
                    val distance = spatialManager.calculateDistance(startLocation, destination)
                    val travelTimeHours = (distance / 5.0).toInt()

                    systemPromptNotes += "Action: Travel to ${destination.name}. Distance: ${distance}km. "

                    val encounter = eventGenerator.checkForEncounter(distance, worldState)
                    if (encounter != null) {
                        systemPromptNotes += "URGENT EVENT: $encounter. Travel interrupted."

                        // Calculate the halfway coordinates
                        val midX = (startLocation.x + destination.x) / 2
                        val midY = (startLocation.y + destination.y) / 2

                        // Move Ain to the path!
                        mainCharacter.currentLocation = Location(
                            name = "The Path between ${startLocation.name} and ${destination.name}",
                            x = midX,
                            y = midY
                        )

                        chronosManager.advanceTime(travelTimeHours / 2)
                    } else {
                        mainCharacter.currentLocation = destination
                        chronosManager.advanceTime(travelTimeHours)
                        systemPromptNotes += "Travel successful. "
                    }
                } else {
                    systemPromptNotes += "Error: Location not found in spatial registry. "
                }
            }
            is Intent.Action -> {
                chronosManager.advanceTime(intent.hoursTaken)
                systemPromptNotes += "Action performed: ${intent.description}. "
            }
            is Intent.Scan -> {
                systemPromptNotes += "Scanning ${intent.target} from ${mainCharacter.currentLocation.name}. "
            }
            is Intent.Unknown -> {
                systemPromptNotes += "Action unclear. "
            }
        }

        val prunedContext = contextPruner.buildSceneContext(mainCharacter, intent)

        // 4. Build the system-only knowledge block
        val systemKnowledge = buildFinalPrompt(systemPromptNotes, prunedContext)

        // 5. Fire the request to Gemini!
        return aiClient.generateNarrative(
            systemPrompt = systemKnowledge,
            userInput = rawInput
        )
    }

    // 6. Notice we removed the USER INPUT section, as it goes into a different JSON field now
    private fun buildFinalPrompt(systemNotes: String, prunedContext: String): String {
        return """
            SYSTEM KNOWLEDGE:
            Time: ${worldState.currentHour}:00
            Mist State: ${worldState.mistState}
            Location: ${mainCharacter.currentLocation.name}
            Engine Notes: $systemNotes
            
            $prunedContext
            
            INSTRUCTION: Write the resulting narrative prose based ONLY on the user input and system knowledge. Keep it to one concise paragraph.
        """.trimIndent()
    }
}