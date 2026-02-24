package io.github.kotlin.fibonacci.logic.managers

import io.github.kotlin.fibonacci.domain.models.*
import kotlin.math.pow
import kotlin.math.sqrt

class SpatialManager(private val initialMap: List<Location>) {
    private val worldMap = initialMap.toMutableList()

    fun getLocation(name: String): Location? {
        return worldMap.find { officialLocation ->
            officialLocation.name.contains(name, ignoreCase = true)
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