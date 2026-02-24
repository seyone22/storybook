package io.github.kotlin.fibonacci.logic.orchestrator

import io.github.kotlin.fibonacci.domain.models.*
import io.github.kotlin.fibonacci.logic.context.ContextPruner
import io.github.kotlin.fibonacci.logic.managers.*
import io.github.kotlin.fibonacci.logic.ai.AiClient

class StoryOrchestrator(
    private val spatialManager: SpatialManager,
    private val chronosManager: ChronosManager,
    private val eventGenerator: EventGenerator,
    private val worldState: WorldState,
    private val mainCharacter: Character,
    private val contextPruner: ContextPruner,
    private val aiClient: AiClient,
    private val memoryManager: MemoryManager
) {

    suspend fun processTurn(rawInput: String): String {
        // --- PRE-COMPUTE CONTEXT ---
        // Fetch the global truth and the recent history
        val globalTruth = contextPruner.buildGlobalContext(mainCharacter)
        val storySoFar = memoryManager.getRecentHistory() // <-- 2. Read memory

        // --- STAGE 1: THE DIRECTOR (PRO) ---
        // Pass the history to the Director so it understands the context of the user's action
        val directorPlaybook = aiClient.getDirectorPlaybook(
            rawInput, worldState, mainCharacter, globalTruth, storySoFar
        )

        // --- KOTLIN ENGINE: STATE UPDATES ---
        val systemNotes = applyMechanicalUpdates(directorPlaybook.intent)

        // --- STAGE 2: THE ACTORS (FLASH) ---
        val performances = directorPlaybook.characterCues.map { cue ->
            val filteredContext = contextPruner.filterForCharacter(cue.characterName, cue.directive)
            // Actors need the history too, so they don't repeat the same dialogue
            aiClient.generateActorPerformance(cue.characterName, filteredContext, rawInput, storySoFar)
        }

        // --- STAGE 3: THE COMPOSITOR (FLASH) ---
        val finalProse = aiClient.composeFinalNarrative(
            userInput = rawInput,
            performances = performances,
            systemNotes = systemNotes + " " + directorPlaybook.narrativeNotes,
            worldState = worldState,
            storySoFar = storySoFar // <-- 3. Pass history to the writer
        )

        // --- POST-COMPUTE MEMORY ---
        // Save the brand new paragraph into the memory manager
        memoryManager.addMemory(finalProse) // <-- 4. Save memory

        return finalProse
    }

    // Extracted the math/state logic out of the main loop to keep it clean
    private fun applyMechanicalUpdates(intent: Intent): String {
        var systemNotes = ""

        when (intent) {
            is Intent.Travel -> {
                val destination = spatialManager.getLocation(intent.destinationName)
                val startLocation = mainCharacter.currentLocation

                if (destination != null) {
                    val distance = spatialManager.calculateDistance(startLocation, destination)
                    val travelTimeHours = (distance / 5.0).toInt()

                    systemNotes += "Action: Travel to ${destination.name}. Distance: ${distance}km. "

                    val encounter = eventGenerator.checkForEncounter(distance, worldState)
                    if (encounter != null) {
                        systemNotes += "URGENT EVENT: $encounter. Travel interrupted. "

                        val midX = (startLocation.x + destination.x) / 2
                        val midY = (startLocation.y + destination.y) / 2

                        mainCharacter.currentLocation = Location(
                            name = "The Path between ${startLocation.name} and ${destination.name}",
                            x = midX,
                            y = midY
                        )
                        chronosManager.advanceTime(travelTimeHours / 2)
                    } else {
                        mainCharacter.currentLocation = destination
                        chronosManager.advanceTime(travelTimeHours)
                        systemNotes += "Travel successful. "
                    }
                } else {
                    systemNotes += "Error: Location not found in spatial registry. "
                }
            }
            is Intent.Action -> {
                chronosManager.advanceTime(intent.hoursTaken)
                systemNotes += "Action performed: ${intent.description}. "
            }
            is Intent.Scan -> {
                systemNotes += "Scanning ${intent.target} from ${mainCharacter.currentLocation.name}. "
            }
            is Intent.Unknown -> {
                systemNotes += "Action unclear or purely conversational. "
            }
        }

        return systemNotes
    }

    suspend fun startSession(): String {
        // 1. Wipe any old memory if we are restarting the game
        memoryManager.clear()

        // 2. Fetch the lore for the starting room and the character's background
        val startingContext = contextPruner.buildGlobalContext(mainCharacter)

        // 3. Ask the AI to paint the initial scene
        val prologueProse = aiClient.generatePrologue(worldState, mainCharacter, startingContext)

        // 4. Save this to memory! This ensures the AI remembers where Ain was
        // standing when the player finally types their first command.
        memoryManager.addMemory(prologueProse)

        return prologueProse
    }
}