package io.github.kotlin.fibonacci.logic.managers

import io.github.kotlin.fibonacci.domain.models.*
import kotlin.math.pow
import kotlin.math.sqrt

class SpatialManager(private val worldMap: List<Location>) {

    fun getLocation(name: String): Location? {
        // Check if the Official Name in our map contains the keyword provided by the user
        val x = worldMap.find { officialLocation ->
            officialLocation.name.contains(name, ignoreCase = true)
        }

        print("Finding location...")
        print(x?.name)

        return x
    }

    fun calculateDistance(loc1: Location, loc2: Location): Double {
        // Basic Euclidean distance for the 2D grid
        return sqrt((loc2.x - loc1.x).toDouble().pow(2) + (loc2.y - loc1.y).toDouble().pow(2))
    }
}

class ChronosManager(private val state: WorldState) {
    fun advanceTime(hours: Int) {
        state.currentHour = (state.currentHour + hours) % 24
        // Logic to change Mist State based on time could go here
        if (state.currentHour in 18..23 || state.currentHour in 0..4) {
            state.mistState = "Thick"
        } else {
            state.mistState = "Clear"
        }
    }
}

class EventGenerator {
    fun checkForEncounter(distance: Double, state: WorldState): String? {
        // The longer the distance and thicker the mist, the higher the chance of Ghouls
        val encounterChance = if (state.mistState == "Thick") 0.6 else 0.1
        val isEncounter = kotlin.random.Random.nextDouble() < encounterChance

        return if (isEncounter && distance > 10.0) {
            "Intercepted by Mist Ghouls!"
        } else {
            null
        }
    }
}