package io.github.kotlin.fibonacci.logic

import io.github.kotlin.fibonacci.domain.models.Character
import io.github.kotlin.fibonacci.domain.models.Location
import io.github.kotlin.fibonacci.domain.models.WorldState
import io.github.kotlin.fibonacci.logic.context.ContextPruner
import io.github.kotlin.fibonacci.logic.managers.CharacterManager
import io.github.kotlin.fibonacci.logic.managers.MemoryManager
import io.github.kotlin.fibonacci.logic.managers.SpatialManager
import io.github.kotlin.fibonacci.logic.orchestrator.StoryOrchestrator

/**
 * The unified Facade / Dashboard for the entire game.
 * The UI/CLI should ONLY interact with this class.
 */
class StoryEngine(
    private val orchestrator: StoryOrchestrator,
    private val worldState: WorldState,
    private val mainCharacter: Character,
    private val spatialManager: SpatialManager,
    private val characterManager: CharacterManager,
    private val memoryManager: MemoryManager,
    private val pruner: ContextPruner
) {

    // ==========================================
    // 1. CORE GAME LOOP ACTIONS (The "Steering Wheel")
    // ==========================================
    suspend fun start(): String {
        return orchestrator.startSession()
    }

    suspend fun playTurn(input: String): String {
        return orchestrator.processTurn(input)
    }

    // ==========================================
    // 2. PLAYER STATE QUERIES (The "Dashboard Dials")
    // ==========================================
    fun getPlayer(): Character = mainCharacter

    fun getPlayerLocation(): Location = mainCharacter.currentLocation

    fun getNearbyLocations(): List<Location> {
        return mainCharacter.currentLocation.connectedLocations.mapNotNull {
            spatialManager.getLocation(it)
        }
    }

    fun getPeopleInRoom(): List<Character> {
        return characterManager.getCharactersInLocation(mainCharacter.currentLocation.id)
            .filter { it.id != mainCharacter.id }
    }

    // --- NEWLY ADDED STATE QUERIES FOR CLI ---
    fun getWalletBalance(): Map<String, Int> = mainCharacter.wallet

    fun getActiveObjectives(): Map<String, String> = mainCharacter.activeObjectives

    fun getKnownSecrets(): List<String> = mainCharacter.knownInformation

    // ==========================================
    // 3. SYSTEM & DEBUG QUERIES (The "Mechanic's Diagnostic Port")
    // ==========================================
    fun getWorldTime(): String = worldState.timeString

    suspend fun getFullMemoryLog(): String = memoryManager.getRecentHistory()

    fun debugGetCharacter(name: String): Character? {
        return characterManager.getAllCharacters().find { it.name.equals(name, ignoreCase = true) }
    }

    fun debugGetActivePromptContext(): String {
        val localChars = getPeopleInRoom()
        val adjacent = getNearbyLocations()
        return pruner.buildGlobalContext(mainCharacter, localChars, adjacent)
    }
}