package io.github.kotlin.fibonacci.logic.managers

import io.github.kotlin.fibonacci.domain.models.*
import kotlin.math.pow
import kotlin.math.sqrt

class SpatialManager(private val initialMap: List<Location>) {
    private val worldMap = initialMap.toMutableList()

    fun getLocation(identifier: String?): Location? {
        if (identifier.isNullOrBlank()) return null

        // 1. Strip all spaces, punctuation, and special characters, then make lowercase
        // E.g., " Gar Village. " becomes "garvillage"
        val normalizedInput = identifier.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()

        if (normalizedInput.isEmpty()) return null

        return worldMap.find { loc ->
            // 2. Normalize the engine's IDs and Names the exact same way
            val normalizedId = loc.id.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
            val normalizedName = loc.name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()

            // 3. Compare the stripped versions
            normalizedId == normalizedInput || normalizedName.contains(normalizedInput)
        }
    }

    fun addLocation(newLoc: Location) {
        if (worldMap.none { it.name == newLoc.name }) {
            worldMap.add(newLoc)
        }
    }

    fun calculateDistance(loc1: Location, loc2: Location): Double {
        return sqrt((loc2.x - loc1.x).toDouble().pow(2) + (loc2.y - loc1.y).toDouble().pow(2))
    }
}

// NEW: To track and store hallucinated NPCs
class CharacterManager(initialCharacters: List<Character>) {
    private val activeCharacters = initialCharacters.toMutableList()

    fun addCharacter(npc: Character) {
        activeCharacters.add(npc)
    }

    fun getCharactersInLocation(locationId: String): List<Character> {
        return activeCharacters.filter { it.currentLocation.id == locationId }
    }

    // NEW: Returns all characters currently tracked by the engine
    fun getAllCharacters(): List<Character> {
        return activeCharacters.toList()
    }
}

class ChronosManager(private val state: WorldState) {
    fun advanceTime(minutes: Int) {
        // Absolute Epoch upgrade!
        state.absoluteTimeMinutes += minutes

        // Dynamically compute mist based on the new currentHour property
        if (state.currentHour in 18..23 || state.currentHour in 0..4) {
            state.mistState = "Thick"
        } else {
            state.mistState = "Clear"
        }
    }
}

class EventGenerator {
    fun checkForEncounter(distance: Double, state: WorldState): String? {
        val encounterChance = if (state.mistState == "Thick") 0.6 else 0.1
        val isEncounter = kotlin.random.Random.nextDouble() < encounterChance

        return if (isEncounter && distance > 10.0) {
            "Intercepted by Mist Ghouls!"
        } else {
            null
        }
    }
}