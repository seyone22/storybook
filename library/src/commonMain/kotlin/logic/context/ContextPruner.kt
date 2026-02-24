package io.github.kotlin.fibonacci.logic.context

import io.github.kotlin.fibonacci.domain.models.*

class ContextPruner(private val globalLoreDatabase: List<LoreFragment>) {

    // --- STAGE 1: FOR THE DIRECTOR (PRO) ---
    // The Director needs to know the ABSOLUTE TRUTH to drive the plot.
    fun buildGlobalContext(actor: Character): String {
        val currentLocation = actor.currentLocation.name
        val activeContext = mutableListOf<String>()

        // The Director sees ALL lore for the location, regardless of secrets
        val locationLore = globalLoreDatabase.filter { it.locationTag == currentLocation }
        activeContext.addAll(locationLore.map { "GLOBAL FACT: ${it.text}" })

        if (actor.currentLocation.name.startsWith("The Path")) {
            activeContext.add("GLOBAL FACT: You are in the dangerous wilds between landmarks. The Mist is unpredictable here.")
        }

        return if (activeContext.isEmpty()) {
            "No specific historical context for this area."
        } else {
            "ABSOLUTE WORLD KNOWLEDGE:\n" + activeContext.joinToString("\n")
        }
    }

    // --- STAGE 2: FOR THE ACTORS (FLASH) ---
    // This is the KNOWLEDGE GUARD. It prevents characters from becoming omniscient.
    fun filterForCharacter(characterName: String, directive: String): String {

        // 1. Get lore specific to this character's personality/history
        val characterLore = globalLoreDatabase.filter {
            it.characterTag.equals(characterName, ignoreCase = true)
        }

        // 2. Get global truths that everyone knows
        val commonKnowledge = globalLoreDatabase.filter { it.isGlobalTruth }

        // 3. (Optional V2) - Check if the character has specific 'requiredSecrets'
        // to know about hidden things in the room.

        val knownLoreText = (characterLore + commonKnowledge)
            .distinct()
            .joinToString("\n") { "- ${it.text}" }

        // 4. In a production build, you would run a regex here to scrub "Secret Keywords"
        // (like 'Rimefrost') from the Director's directive if the character doesn't know it.
        // For now, we provide the clean prompt block:

        return """
            DIRECTOR'S CUE: $directive
            
            YOUR INTERNAL LORE & KNOWLEDGE:
            $knownLoreText
            
            Remember: Do not act on or mention any knowledge outside of your specific lore.
        """.trimIndent()
    }
}