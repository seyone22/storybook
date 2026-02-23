package io.github.kotlin.fibonacci.logic.orchestrator

import io.github.kotlin.fibonacci.domain.models.*
import io.github.kotlin.fibonacci.logic.context.ContextPruner
import io.github.kotlin.fibonacci.logic.managers.*

class StoryOrchestrator(
    private val spatialManager: SpatialManager,
    private val chronosManager: ChronosManager,
    private val eventGenerator: EventGenerator,
    private val worldState: WorldState,
    private val mainCharacter: Character,
    private val contextPruner: ContextPruner // <-- 1. Add it here
) {

    // V1: This mocks the LLM intent fetching.
    // In V2, you will send `rawInput` to an LLM and parse the JSON response into this Intent object.
    private fun fetchIntent(rawInput: String): Intent {
        val lowerInput = rawInput.lowercase()
        return when {
            lowerInput.contains("run to") || lowerInput.contains("go to") || lowerInput.contains("runs to") -> {
                // Extremely naive extraction for the prototype
                val dest = if (lowerInput.contains("village")) "Village" else "Unknown"
                Intent.Travel(dest)
            }
            lowerInput.contains("looks around") -> Intent.Scan("Surroundings")
            lowerInput.contains("spend the night") -> Intent.Action(rawInput, hoursTaken = 8)
            lowerInput.contains("finishes breakfast") -> Intent.Action(rawInput, hoursTaken = 1)
            else -> Intent.Unknown
        }
    }

    fun processTurn(rawInput: String): String {
        val intent = fetchIntent(rawInput)
        var systemPromptNotes = ""

        when (intent) {
            is Intent.Travel -> {
                val destination = spatialManager.getLocation(intent.destinationName)
                if (destination != null) {
                    val distance = spatialManager.calculateDistance(mainCharacter.currentLocation, destination)
                    val travelTimeHours = (distance / 5.0).toInt() // Assuming 5km/h walking speed

                    systemPromptNotes += "Action: Travel to ${destination.name}. Distance: ${distance}km. "

                    // Check for events during travel
                    val encounter = eventGenerator.checkForEncounter(distance, worldState)
                    if (encounter != null) {
                        systemPromptNotes += "URGENT EVENT: $encounter. Travel interrupted."
                        chronosManager.advanceTime(travelTimeHours / 2) // Interrupted halfway
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
                // Costs no time, just fetches context
                systemPromptNotes += "Scanning ${intent.target} from ${mainCharacter.currentLocation.name}. "
            }
            is Intent.Unknown -> {
                systemPromptNotes += "Action unclear. "
            }
        }


        // The world state has updated. Ain has moved or waited.
        // Now, ask the Pruner what lore exists in this current state.
        val prunedContext = contextPruner.buildSceneContext(mainCharacter, intent)

        // 3. Pass the pruned context into your prompt builder
        return buildFinalPrompt(rawInput, systemPromptNotes, prunedContext)
    }

    private fun buildFinalPrompt(userInput: String, systemNotes: String, prunedContext: String): String {
        return """
            SYSTEM KNOWLEDGE:
            Time: ${worldState.currentHour}:00
            Mist State: ${worldState.mistState}
            Location: ${mainCharacter.currentLocation.name}
            Engine Notes: $systemNotes
            
            $prunedContext  // <-- 5. Inject the lore into the prompt
            
            USER INPUT: "$userInput"
            
            INSTRUCTION: Write the resulting narrative prose based on the user input and system knowledge.
        """.trimIndent()
    }
}