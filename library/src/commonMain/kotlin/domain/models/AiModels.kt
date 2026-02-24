package io.github.kotlin.fibonacci.domain.models

import kotlinx.serialization.Serializable

// --- Standard API Wrappers ---
@Serializable
data class AiResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: Message
)

@Serializable
data class Message(
    val content: String
)

// --- Engine DTOs ---
@Serializable
data class IntentDto(
    val type: String,
    val destinationName: String? = null,
    val target: String? = null,
    val description: String? = null,
    val timeCostMinutes: Int = 0 // CHANGED: Now uses minutes for the Absolute Epoch
)

@Serializable
data class CharacterCueDto(
    val characterName: String,
    val emotionalState: String,
    val directive: String
)

@Serializable
data class NewLocationDto(
    val id: String,
    val name: String,
    val description: String,
    val connectedToLocationId: String
)

@Serializable
data class NewCharacterDto(
    val name: String,
    val locationId: String,
    val background: String,
    val personality: String
)

// NEW: Catching dynamic, agnostic state changes!
@Serializable
data class StateUpdateDto(
    val statChanges: Map<String, String>? = null,
    val walletChanges: Map<String, Int>? = null, // e.g., {"Stags": -5}
    val relationshipChanges: Map<String, String>? = null, // e.g., {"Ayasa": "Owes a favor"}
    val inventoryGained: List<String>? = null,
    val inventoryLost: List<String>? = null
)

@Serializable
data class DirectorPlaybookDto(
    val intentJson: IntentDto,
    val characterCues: List<CharacterCueDto>,
    val narrativeNotes: String,
    val newlyDiscoveredLocations: List<NewLocationDto> = emptyList(),
    val newlyIntroducedCharacters: List<NewCharacterDto> = emptyList(),
    val stateUpdates: StateUpdateDto? = null, // Applies the agnostic map changes
    val requestedImagePrompt: String? = null // Triggers UI image generation
)