package io.github.kotlin.fibonacci.logic.context

import io.github.kotlin.fibonacci.domain.models.*

class ContextPruner(initialLoreDatabase: List<LoreFragment>) {

    private val globalLoreDatabase = initialLoreDatabase.toMutableList()

    fun addGlobalLore(lore: LoreFragment) {
        if (globalLoreDatabase.none { it.id == lore.id }) {
            globalLoreDatabase.add(lore)
        }
    }

    // --- STAGE 1: FOR THE DIRECTOR (PRO) ---
    fun buildGlobalContext(actor: Character): String {
        val currentLocation = actor.currentLocation.name
        val activeContext = mutableListOf<String>()

        // 1. Inject Agnostic Character Sheet
        activeContext.add("CHARACTER SHEET:")
        activeContext.add("- Name: ${actor.name}")
        activeContext.add("- Background: ${actor.background}")
        activeContext.add("- Personality: ${actor.personality}")
        if (actor.stats.isNotEmpty()) activeContext.add("- Stats: ${actor.stats.entries.joinToString { "${it.key}: ${it.value}" }}")
        if (actor.wallet.isNotEmpty()) activeContext.add("- Wealth: ${actor.wallet.entries.joinToString { "${it.key}: ${it.value}" }}")
        if (actor.relationships.isNotEmpty()) activeContext.add("- Relationships: ${actor.relationships.entries.joinToString { "${it.key}: ${it.value}" }}")
        activeContext.add("- Inventory: ${if (actor.inventory.isEmpty()) "Empty" else actor.inventory.joinToString(", ")}")
        activeContext.add("\nLOCATION FACTS:")

        // 2. Load Location Lore
        val locationLore = globalLoreDatabase.filter { it.locationTag == currentLocation }
        activeContext.addAll(locationLore.map { "GLOBAL FACT: ${it.text}" })

        if (actor.currentLocation.name.startsWith("The Path")) {
            activeContext.add("GLOBAL FACT: You are in the dangerous wilds between landmarks.")
        }

        return if (activeContext.isEmpty()) {
            "No specific historical context for this area."
        } else {
            "ABSOLUTE WORLD KNOWLEDGE:\n" + activeContext.joinToString("\n")
        }
    }

    // --- STAGE 2: FOR THE ACTORS (FLASH) ---
    fun filterForCharacter(characterName: String, directive: String): String {
        val characterLore = globalLoreDatabase.filter {
            it.characterTag.equals(characterName, ignoreCase = true)
        }
        val commonKnowledge = globalLoreDatabase.filter { it.isGlobalTruth }

        val knownLoreText = (characterLore + commonKnowledge)
            .distinct()
            .joinToString("\n") { "- ${it.text}" }

        return """
            DIRECTOR'S CUE: $directive
            
            YOUR INTERNAL LORE & KNOWLEDGE:
            $knownLoreText
            
            Remember: Do not act on or mention any knowledge outside of your specific lore.
        """.trimIndent()
    }
}