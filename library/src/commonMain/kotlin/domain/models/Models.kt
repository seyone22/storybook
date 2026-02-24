package io.github.kotlin.fibonacci.domain.models

// --- 1. SPATIAL MODELS ---
data class Location(
    val id: String,
    val name: String,
    val x: Int,
    val y: Int,
    val isHidden: Boolean = false,
    val connectedLocations: List<String> = emptyList() // Topological edges
)

// --- 2. TEMPORAL & STATE MODELS ---
data class WorldState(
    // The Absolute Epoch: Default is Day 1 at 07:00 AM (7 * 60 = 420)
    var absoluteTimeMinutes: Int = 420,
    var mistState: String = "Clear", // Or any atmospheric condition
    val globalTension: Int = 0
) {
    // Computed Time Properties
    val currentDay: Int
        get() = (absoluteTimeMinutes / (24 * 60)) + 1

    val currentHour: Int
        get() = (absoluteTimeMinutes / 60) % 24

    val currentMinute: Int
        get() = absoluteTimeMinutes % 60

    val timeString: String
        get() = "Day $currentDay, ${currentHour.toString().padStart(2, '0')}:${currentMinute.toString().padStart(2, '0')}"
}

// --- 3. THEME & RULE CONFIGURATION ---
data class WorldConfig(
    val genre: String, // e.g., "Dark Fantasy", "Cyberpunk"
    val tone: String,  // e.g., "Grim and suspenseful"
    val narratorStyle: String, // e.g., "Third-person omniscient"
    val startDate: String = "0",
    val settingSummary: String = "",
    val directorInstructions: String = "",

    // Agnostic Rules & Mechanics
    val coreRules: List<String> = emptyList(), // e.g., ["No Plot Armor", "Zero Moral Armor"]
    val mechanicsLore: String = "", // e.g., "Magic causes physical strain."
    val economyRules: String = "" // e.g., "1 Stag = 12 Chits. Prices are steep."
)

// --- 4. INTENT ROUTING ---
sealed class Intent {
    data class Travel(val destinationName: String) : Intent()
    data class Action(val description: String, val timeCostMinutes: Int) : Intent() // Upgraded to minutes
    data class Scan(val target: String) : Intent()
    object Unknown : Intent()
}

// --- 5. LORE & KNOWLEDGE ---
data class LoreFragment(
    val id: String,
    val category: String, // NEW: e.g., "Location", "Faction", "Mechanic", "Bestiary", "Character"
    val text: String,
    val locationTag: String? = null,
    val characterTag: String? = null,
    val requiredSecret: String? = null,
    val visibleTo: List<String> = emptyList(),
    val isGlobalTruth: Boolean = false
)

// --- 6. AGNOSTIC CHARACTER MODEL ---
data class Character(
    val id: String, // Differentiate from display name
    val name: String,
    var currentLocation: Location,

    // Core Identity
    val background: String = "A traveler of unknown origins.",
    val personality: String = "Neutral and observant.",

    // Dynamic, Agnostic Systems (Plug & Play)
    val stats: MutableMap<String, String> = mutableMapOf(), // e.g., {"Strength": "E", "Mana": "S"}
    val wallet: MutableMap<String, Int> = mutableMapOf(), // e.g., {"Crowns": 0, "Stags": 5}
    val relationships: MutableMap<String, String> = mutableMapOf(), // e.g., {"Ayasa": "Owes 10 stags"}

    val activeObjectives: MutableMap<String, String> = mutableMapOf(),
    val inventory: MutableList<String> = mutableListOf(),
    var knownInformation: List<String> = emptyList()
)

// --- 7. AI ORCHESTRATION MODELS ---
data class CharacterCue(
    val characterName: String,
    val emotionalState: String,
    val directive: String
)

data class DiscoveredLocation(
    val id: String,
    val name: String,
    val description: String,
    val connectedToLocationId: String
)

data class DiscoveredCharacter(
    val name: String,
    val locationId: String,
    val background: String,
    val personality: String
)

// NEW: The domain representation of map mutations
data class StateUpdate(
    val statChanges: Map<String, String>? = null,
    val walletChanges: Map<String, Int>? = null,
    val relationshipChanges: Map<String, String>? = null,
    // NEW: Inventory Management
    val objectiveChanges: Map<String, String>? = null, // NEW
    val inventoryGained: List<String>? = null,
    val inventoryLost: List<String>? = null
)

// UPDATED: Add it to the Playbook
data class DirectorPlaybook(
    val intent: Intent,
    val characterCues: List<CharacterCue>,
    val narrativeNotes: String,
    val newlyDiscoveredLocations: List<DiscoveredLocation> = emptyList(),
    val newlyIntroducedCharacters: List<DiscoveredCharacter> = emptyList(),
    val stateUpdates: StateUpdate? = null, // <-- The missing link!
    val requestedImagePrompt: String? = null
)