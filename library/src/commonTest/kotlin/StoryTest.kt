package com.storybook.engine

import io.github.kotlin.fibonacci.domain.models.*
import io.github.kotlin.fibonacci.logic.managers.*
import io.github.kotlin.fibonacci.logic.context.*
import io.github.kotlin.fibonacci.logic.orchestrator.StoryOrchestrator
import kotlin.test.Test
import kotlin.test.assertTrue

class StoryEngineTest {

    @Test
    fun testAinTravelScenarioWithLore() {
        // 1. Setup the World Map
        val manor = Location("Gracia Manor", x = 0, y = 0)
        val village = Location("Frostspine Village", x = 0, y = 100)
        val worldMap = listOf(manor, village)

        // 2. Setup Lore Database (The Context Pruner's Source)
        val globalLore = listOf(
            LoreFragment(
                id = "manor_history",
                text = "Gracia Manor has stood for centuries, protected by the old wards.",
                locationTag = "Gracia Manor"
            ),
            LoreFragment(
                id = "apothecary_secret",
                text = "The Apothecary in Frostspine Village is secretly a member of the Underground Alchemists.",
                locationTag = "Frostspine Village",
                characterTag = "Apothecary"
            ),
            LoreFragment(
                id = "hidden_glade",
                text = "There is a hidden glade nearby that only opens during the Thick Mist.",
                locationTag = "Frostspine Village",
                requiredSecret = "GLADE_MAP" // Ain can't see this yet!
            )
        )

        // 3. Initialize State
        val worldState = WorldState(currentHour = 7, mistState = "Clear")
        // Note: ain.knownInformation is empty, so he won't see the Hidden Glade lore.
        val ain = Character("Ain", currentLocation = manor, knownInformation = emptyList())

        // 4. Initialize Subsystems
        val spatial = SpatialManager(worldMap)
        val chronos = ChronosManager(worldState)
        val events = EventGenerator()
        val pruner = ContextPruner(globalLore)

        val orchestrator = StoryOrchestrator(
            spatial, chronos, events, worldState, ain, pruner
        )

        println("--- STARTING SIMULATION ---")
        println("Initial State: Time=${worldState.currentHour}:00, Location=${ain.currentLocation.name}")

        // 5. Action 1: Breakfast at the Manor
        // Should show Manor lore, but NOT Village lore.
        println("\n> Action: Ain finishes breakfast.")
        val result1 = orchestrator.processTurn("ain finishes breakfast.")
        println(result1)

        // 6. Action 2: Travel to Village
        // Should drop Manor lore and load Village/Apothecary lore.
        println("\n> Action: The journey to the village.")
        val result2 = orchestrator.processTurn("He then runs to the village to go find the apothecary.")
        println(result2)

        // 7. Final Status Check
        println("\n--- FINAL WORLD STATE ---")
        println("Current Time: ${worldState.currentHour}:00")
        println("Ain's Position: ${ain.currentLocation.name}")

        assertTrue(result2.contains("Underground Alchemists"), "Village lore should be present")
        assertTrue(!result2.contains("Gracia Manor has stood"), "Manor lore should have been pruned")
    }
}