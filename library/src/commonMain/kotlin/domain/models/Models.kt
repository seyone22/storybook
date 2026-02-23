package io.github.kotlin.fibonacci.domain.models

data class Location(
    val name: String,
    val x: Int,
    val y: Int,
    val isHidden: Boolean = false
)

data class Character(
    val name: String,
    var currentLocation: Location,
    val knownInformation: List<String> = emptyList()
)

data class WorldState(
    var currentHour: Int = 8, // 24-hour clock
    var mistState: String = "Clear", // e.g., "Clear", "Rising", "Thick"
    val globalTension: Int = 0
)

// Sealed classes are perfect for routing natural language intents
sealed class Intent {
    data class Travel(val destinationName: String) : Intent()
    data class Action(val description: String, val hoursTaken: Int) : Intent()
    data class Scan(val target: String) : Intent()
    object Unknown : Intent()
}

// Represents a single piece of history, character sheet, or location description
data class LoreFragment(
    val id: String,
    val text: String,
    val locationTag: String? = null,   // Only load if Ain is here (e.g., "Frostspine Village")
    val characterTag: String? = null,  // Only load if Ain interacts with them (e.g., "Apothecary")
    val requiredSecret: String? = null // HIDDEN INFO: Only load if Ain knows this secret
)