package com.storybook.engine

import io.github.kotlin.fibonacci.domain.models.*
import io.github.kotlin.fibonacci.logic.managers.*
import io.github.kotlin.fibonacci.logic.context.*
import io.github.kotlin.fibonacci.logic.orchestrator.StoryOrchestrator
import io.github.kotlin.fibonacci.logic.ai.GeminiAiClient
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class StoryEngineTest {

    @Test
    fun testAinTravelScenarioWithLore() = runTest { // <-- Wrapped in runTest for Coroutines
        // 1. Setup the World Map
        val manor = Location("Gracia Manor", x = 0, y = 0)
        val village = Location("Frostspine Village", x = 0, y = 100)
        val worldMap = listOf(manor, village)

        // 2. Setup Lore Database
        val globalLore = listOf(
            LoreFragment("manor_history", "Gracia Manor has stood for centuries, protected by the old wards.", "Gracia Manor"),
            LoreFragment("apothecary_secret", "The Apothecary in Frostspine Village is secretly a member of the Underground Alchemists.", "Frostspine Village", "Apothecary"),
            LoreFragment("hidden_glade", "There is a hidden glade nearby that only opens during the Thick Mist.", "Frostspine Village", null, "GLADE_MAP")
        )

        // 3. Initialize State
        val worldState = WorldState(currentHour = 7, mistState = "Clear")
        val ain = Character("Ain", currentLocation = manor, knownInformation = emptyList())

        // 4. Initialize Network Client & AI Bridge
        val ktorClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 🚨 PASTE YOUR API KEY HERE 🚨
        val aiClient = GeminiAiClient(apiKey = "YOUR_API_KEY_HERE", httpClient = ktorClient)

        // 5. Initialize Subsystems
        val spatial = SpatialManager(worldMap)
        val chronos = ChronosManager(worldState)
        val events = EventGenerator()
        val pruner = ContextPruner(globalLore)

        // 6. Pass aiClient into the Orchestrator
        val orchestrator = StoryOrchestrator(
            spatial, chronos, events, worldState, ain, pruner, aiClient
        )

        println("--- STARTING SIMULATION ---")
        println("Initial State: Time=${worldState.currentHour}:00, Location=${ain.currentLocation.name}")

        // 7. Action 1: Breakfast at the Manor
        println("\n> Action: Ain finishes breakfast.")
        val result1 = orchestrator.processTurn("Ain finishes breakfast.")
        println("\n[AI NARRATIVE 1]:\n$result1")

        // 8. Action 2: Travel to Village
        println("\n> Action: The journey to the village.")
        val result2 = orchestrator.processTurn("He then runs to the village to go find the apothecary.")
        println("\n[AI NARRATIVE 2]:\n$result2")

        // 9. Final Status Check
        println("\n--- FINAL WORLD STATE ---")
        println("Current Time: ${worldState.currentHour}:00")
        println("Ain's Position: ${ain.currentLocation.name}")

        // Note on Testing LLMs: Strict string assertions on AI output can be flaky!
        // We are checking if the AI decided to mention the Alchemists in its prose.
        assertTrue(result2.contains("Alchemist", ignoreCase = true), "AI should have incorporated the Apothecary lore.")
        assertTrue(!result2.contains("Gracia Manor", ignoreCase = true), "AI should not mention the Manor anymore.")
    }
}