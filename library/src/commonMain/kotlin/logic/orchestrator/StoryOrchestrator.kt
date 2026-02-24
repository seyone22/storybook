package io.github.kotlin.fibonacci.logic.orchestrator

import io.github.kotlin.fibonacci.domain.models.*
import io.github.kotlin.fibonacci.logic.context.ContextPruner
import io.github.kotlin.fibonacci.logic.managers.*
import io.github.kotlin.fibonacci.logic.ai.AiClient

class StoryOrchestrator(
    private val spatialManager: SpatialManager,
    private val characterManager: CharacterManager, // INJECTED
    private val chronosManager: ChronosManager,
    private val eventGenerator: EventGenerator,
    private val worldState: WorldState,
    private val worldConfig: WorldConfig,
    private val mainCharacter: Character,
    private val contextPruner: ContextPruner,
    private val aiClient: AiClient,
    private val memoryManager: MemoryManager
) {

    suspend fun startSession(): String {
        // 1. Wipe any old memory
        memoryManager.clear()

        // 2. Gather Tier 1 and Tier 2 Spatial Data for the starting room
        val localCharacters = characterManager.getCharactersInLocation(mainCharacter.currentLocation.id)
        val adjacentLocations = mainCharacter.currentLocation.connectedLocations.mapNotNull {
            spatialManager.getLocation(it)
        }

        // 3. Fetch the rich, context-aware lore
        val startingContext = contextPruner.buildGlobalContext(mainCharacter, localCharacters, adjacentLocations)

        // 4. Ask the AI to paint the initial scene with all that glorious context
        val prologueProse = aiClient.generatePrologue(worldState, mainCharacter, startingContext, worldConfig)

        // 5. Save to memory
        memoryManager.addMemory(prologueProse)

        return prologueProse
    }

    suspend fun processTurn(rawInput: String): String {
        // 1. Gather Tier 1 and Tier 2 Spatial Data
        val localCharacters = characterManager.getCharactersInLocation(mainCharacter.currentLocation.id)
        val adjacentLocations = mainCharacter.currentLocation.connectedLocations.mapNotNull {
            spatialManager.getLocation(it)
        }

        // 2. Pass them to the Pruner!
        val globalTruth = contextPruner.buildGlobalContext(mainCharacter, localCharacters, adjacentLocations)
        val storySoFar = memoryManager.getRecentHistory()

        val directorPlaybook = aiClient.getDirectorPlaybook(
            rawInput, worldState, mainCharacter, globalTruth, storySoFar
        )

        // --- KOTLIN ENGINE: STATE UPDATES ---
        val systemNotes = applyMechanicalUpdates(directorPlaybook.intent)

        // Dynamic State Adjustments (Wallets, Relationships, Stats)
        directorPlaybook.stateUpdates?.let { updates ->
            updates.statChanges?.forEach { (k, v) -> mainCharacter.stats[k] = v }
            updates.walletChanges?.forEach { (k, v) ->
                val current = mainCharacter.wallet[k] ?: 0
                mainCharacter.wallet[k] = current + v
            }
            updates.relationshipChanges?.forEach { (k, v) -> mainCharacter.relationships[k] = v }

            // NEW: Apply Inventory Updates
            updates.inventoryGained?.forEach { mainCharacter.inventory.add(it) }
            updates.inventoryLost?.forEach { mainCharacter.inventory.remove(it) }
        }

        // --- ASSIMILATE HALLUCINATIONS ---
        directorPlaybook.newlyDiscoveredLocations.forEach { newLocDto ->
            val parentLoc = spatialManager.getLocation(newLocDto.connectedToLocationId)
            val newLocation = Location(
                id = newLocDto.id,
                name = newLocDto.name,
                x = parentLoc?.x ?: 0,
                y = parentLoc?.y ?: 0,
                connectedLocations = listOf(newLocDto.connectedToLocationId)
            )
            spatialManager.addLocation(newLocation)
            contextPruner.addGlobalLore(LoreFragment(
                id = newLocDto.id,
                category = "Location",
                text = newLocDto.description,
                locationTag = newLocDto.name
            ))
        }

        directorPlaybook.newlyIntroducedCharacters.forEach { npcDto ->
            val npcLocation = spatialManager.getLocation(npcDto.locationId) ?: mainCharacter.currentLocation
            val newNpc = Character(
                id = "npc_${npcDto.name.replace(" ", "_")}",
                name = npcDto.name,
                currentLocation = npcLocation,
                background = npcDto.background,
                personality = npcDto.personality
            )
            characterManager.addCharacter(newNpc)
            contextPruner.addGlobalLore(LoreFragment(
                id = "lore_${newNpc.id}",
                category = "Character",
                text = "${npcDto.name} - ${npcDto.background}. ${npcDto.personality}",
                characterTag = npcDto.name
            ))
        }

        // --- STAGE 2: THE ACTORS (FLASH) ---
        val performances = directorPlaybook.characterCues.map { cue ->
            val filteredContext = contextPruner.filterForCharacter(cue.characterName, cue.directive)
            aiClient.generateActorPerformance(cue.characterName, filteredContext, rawInput, storySoFar)
        }

        // --- STAGE 3: THE COMPOSITOR (FLASH) ---
        val finalProse = aiClient.composeFinalNarrative(
            userInput = rawInput,
            performances = performances,
            systemNotes = systemNotes + " " + directorPlaybook.narrativeNotes,
            worldState = worldState,
            storySoFar = storySoFar,
            worldConfig = worldConfig
        )

        memoryManager.addMemory(finalProse)
        return finalProse
    }

    private fun applyMechanicalUpdates(intent: Intent): String {
        var systemNotes = ""

        when (intent) {
            is Intent.Travel -> {
                val destination = spatialManager.getLocation(intent.destinationName)
                val startLocation = mainCharacter.currentLocation

                if (destination != null) {
                    val distance = spatialManager.calculateDistance(startLocation, destination)
                    // Convert hours to minutes for the new system (e.g., 5km/h = 12 mins per km)
                    val travelTimeMinutes = ((distance / 5.0) * 60).toInt()

                    systemNotes += "Action: Travel to ${destination.name}. Distance: ${distance}km. "

                    val encounter = eventGenerator.checkForEncounter(distance, worldState)
                    if (encounter != null) {
                        systemNotes += "URGENT EVENT: $encounter. Travel interrupted. "

                        mainCharacter.currentLocation = Location(
                            id = "path_${startLocation.id}_${destination.id}",
                            name = "The Path between ${startLocation.name} and ${destination.name}",
                            x = (startLocation.x + destination.x) / 2,
                            y = (startLocation.y + destination.y) / 2,
                            connectedLocations = listOf(startLocation.id, destination.id)
                        )
                        chronosManager.advanceTime(travelTimeMinutes / 2)
                    } else {
                        mainCharacter.currentLocation = destination
                        chronosManager.advanceTime(travelTimeMinutes)
                        systemNotes += "Travel successful. "
                    }
                } else {
                    systemNotes += "Error: Location not found in spatial registry. "
                }
            }
            is Intent.Action -> {
                // Now uses timeCostMinutes directly
                chronosManager.advanceTime(intent.timeCostMinutes)
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
}