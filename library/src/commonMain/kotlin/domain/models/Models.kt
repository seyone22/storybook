package io.github.kotlin.fibonacci.domain.models

data class Location(
    val name: String,
    val x: Int,
    val y: Int,
    val isHidden: Boolean = false
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

data class LoreFragment(
    val id: String,
    val text: String,
    val locationTag: String? = null,
    val characterTag: String? = null,
    val requiredSecret: String? = null,
    // NEW: A list of character names who intrinsically know this fact
    val visibleTo: List<String> = emptyList(),
    // NEW: If true, everyone knows it. If false, it's a secret.
    val isGlobalTruth: Boolean = false
)

data class Character(
    val name: String,
    var currentLocation: Location,
    // The list of secrets this specific character has discovered
    var knownInformation: List<String> = emptyList()
)

data class DirectorPlaybook(
    val intent: Intent,
    val characterCues: List<CharacterCue>,
    val narrativeNotes: String
)

data class CharacterCue(
    val characterName: String,
    val emotionalState: String,
    val directive: String
)