package io.github.kotlin.fibonacci.logic.context

import io.github.kotlin.fibonacci.domain.models.*

class ContextPruner(private val globalLoreDatabase: List<LoreFragment>) {

    fun buildSceneContext(actor: Character, intent: Intent): String {
        val currentLocation = actor.currentLocation.name
        val activeContext = mutableListOf<String>()

        // RULE 1: Always load the baseline lore for the current location
        val locationLore = globalLoreDatabase.filter { it.locationTag == currentLocation }
        for (lore in locationLore) {
            // Check for hidden information!
            // If the lore has a required secret, Ain must know it to "see" it.
            if (lore.requiredSecret == null || actor.knownInformation.contains(lore.requiredSecret)) {
                activeContext.add(lore.text)
            }
        }

        // RULE 2: Load specific character lore if the user is looking for them
        // (e.g., "Find the apothecary")
        if (intent is Intent.Travel || intent is Intent.Scan) {
            // A naive check for the prototype. If the intent mentions a character tag, load it.
            val target = when (intent) {
                is Intent.Travel -> intent.destinationName
                is Intent.Scan -> intent.target
                else -> ""
            }

            val characterLore = globalLoreDatabase.filter {
                it.characterTag != null && target.contains(it.characterTag, ignoreCase = true)
            }
            activeContext.addAll(characterLore.map { it.text })
        }

        // The Pruner drops everything else. Gracia Manor is completely forgotten
        // by the AI while Ain is in the Village.
        return if (activeContext.isEmpty()) {
            "No specific historical or character context for this area."
        } else {
            "RELEVANT LORE & CHARACTERS:\n" + activeContext.joinToString("\n") { "- $it" }
        }
    }
}