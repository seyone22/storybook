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
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import kotlin.time.Duration.Companion.seconds

class StoryEngineTest {

    @Test
    fun testAinTravelScenarioWithLore() = runTest(timeout = 240.seconds) {
        // --- 1. SPATIAL SETUP ---
        val westWing = Location(id = "loc_west_wing", name = "The West Wing (The Ruin)", x = 0, y = 0, connectedLocations = listOf("loc_grand_hall"))
        val grandHall = Location(id = "loc_grand_hall", name = "The Grand Hall (North Wing)", x = 1, y = 0, connectedLocations = listOf("loc_west_wing", "loc_courtyard"))
        val village = Location(id = "loc_gar_village", name = "Gar Village", x = 0, y = 4, connectedLocations = listOf("loc_courtyard"))
        val worldMap = listOf(westWing, grandHall, village)

        // --- 2. WORLD CONFIGURATION (THE IRON LAWS) ---
        val worldConfig = WorldConfig(
            genre = "Dark Fantasy / Seinen / Political Intrigue",
            tone = "Gritty, Visceral, High-Lethality, Psychological",
            narratorStyle = "Japanese Light Novel aesthetics, highly descriptive, extensive internal monologues",
            coreRules = listOf(
                "No Plot Armor: The Protagonist can lose limbs, be scarred, or be humiliated.",
                "Economy of Action: Everything costs money or favors.",
                "NPC Autonomy: NPCs have their own agendas and will act independently.",
                "Zero Moral Armor: Survival is expensive. If the Protagonist is broke, they cannot buy information or warmth."
            ),
            mechanicsLore = "Magic is called 'Resonance'. It manipulates fundamental physics. Using magic causes 'The Strain' (physical trauma, bleeding, burns). Magic is NOT a gift, it is a transaction that destroys the body.",
            economyRules = "1 Crown = 20 Stags. 1 Stag = 12 Iron Chits. Ayasa charges 2 Stags to clean blood from the carpet."
        )

        // --- 3. LORE DATABASE ---
        val globalLore = listOf(
            LoreFragment("lore_west_wing", "Location", "Damp, unheated, and structurally failing. The stone sweats condensation. It contains the Protagonist's sparse, cold room.", "The West Wing (The Ruin)"),
            LoreFragment("lore_grand_hall", "Location", "Warm, opulent, and smelling of lavender and wax. This is where the Duke's family gathers.", "The Grand Hall (North Wing)"),
            LoreFragment("lore_gar_village", "Location", "A bleak, huddled settlement of timber and thatch, surrounded by a crude wooden palisade. Contains the Gallows and the Church of the Pale Saint.", "Gar Village"),
            LoreFragment("lore_garden_thorns", "Faction", "A shadowy underground guild of assassins and spies disguised as domestic servants. Their philosophy: 'A clean house requires the removal of trash.'", characterTag = "Ayasa"),
            LoreFragment("lore_mist_ghouls", "Bestiary", "Twisted, pale humanoids that live in the Misty Forest. They have no eyes and mimic human voices (crying babies, screaming women) to lure travelers.", isGlobalTruth = true)
        )

        // --- 4. CHARACTER ENTITIES ---
        val ain = Character(
            id = "char_ain",
            name = "Ain",
            currentLocation = westWing,
            background = "The 'Spare' Heir. Isolated in the attic for 3 years after his parents died in the Misty Forest.",
            personality = "Cold & Calculating. Submissive mask.",
            stats = mutableMapOf("Strength" to "D", "Agility" to "C", "Intelligence" to "S", "Mana" to "B"),
            wallet = mutableMapOf("Stags" to 15, "Chits" to 0),
            relationships = mutableMapOf("Ayasa" to "Fear/Tool", "Alistair" to "Hostile"),
            inventory = mutableListOf("Scalpel", "Notepad", "Quill", "Conductive Ink", "Charred Ribbon"),
            knownInformation = listOf("Ain possesses Rimefrost blood, granting cold immunity.", "Ayasa is an assassin from the Garden of Thorns.")
        )

        val ayasa = Character(
            id = "char_ayasa",
            name = "Ayasa",
            currentLocation = westWing, // Starts in the same room!
            background = "Personal Maid / Covert Operative from the Garden of Thorns.",
            personality = "Kuudere/Sadodere. Professional hostility. Uses Keigo mockingly.",
            stats = mutableMapOf("Strength" to "C+", "Agility" to "S", "Intelligence" to "A"),
            wallet = mutableMapOf(),
            relationships = mutableMapOf("Ain" to "Target to protect/mock"),
            inventory = mutableListOf("Twin Daggers (Cleaners)", "Garrote Pocket Watch", "Ballistic Maid Apron"),
            knownInformation = listOf("Ain is a high value target. Standing orders are to observe until deemed too dangerous.")
        )

        val alistair = Character(
            id = "char_alistair",
            name = "Duke Alistair",
            currentLocation = grandHall,
            background = "The Iron Uncle. The Patriarch.",
            personality = "Cold, pragmatic. Values utility over blood.",
            stats = mutableMapOf(), wallet = mutableMapOf(), relationships = mutableMapOf(), inventory = mutableListOf(), knownInformation = emptyList()
        )

        val seraphina = Character(
            id = "char_seraphina",
            name = "Seraphina",
            currentLocation = grandHall,
            background = "The False Flower.",
            personality = "Appears sweet, but is a sadistic sociopath who loves torture.",
            stats = mutableMapOf(), wallet = mutableMapOf(), relationships = mutableMapOf(), inventory = mutableListOf(), knownInformation = emptyList()
        )

        // --- 5. INITIALIZE SUBSYSTEMS ---
        val worldState = WorldState(absoluteTimeMinutes = 420, mistState = "Clear") // Day 1, 07:00 AM

        val ktorClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                requestTimeoutMillis = 240000
                connectTimeoutMillis = 120000
                socketTimeoutMillis = 240000
            }
        }

        // 🚨 PASTE YOUR API KEY HERE 🚨
        val aiClient = GeminiAiClient(apiKey = "F", httpClient = ktorClient)

        val spatial = SpatialManager(worldMap)
        val characterManager = CharacterManager(listOf(ain, ayasa, alistair, seraphina)) // <-- Injected!
        val chronos = ChronosManager(worldState)
        val events = EventGenerator()
        val pruner = ContextPruner(globalLore)
        val memory = TransientMemoryManager()

        val orchestrator = StoryOrchestrator(
            spatial, characterManager, chronos, events, worldState, worldConfig, ain, pruner, aiClient, memory
        )

        println("--- STARTING SIMULATION ---")

        // Turn 0: The Engine sets the stage
        val prologue = orchestrator.startSession()
        println("\n[PROLOGUE]:\n$prologue")

        // Turn 1: The Player reacts to the Prologue
        println("\n> Action: Ain gets out of bed and looks around the room to see if Ayasa is hiding somewhere.")
        val result0 = orchestrator.processTurn("Ain gets out of bed and looks around the room to see if Ayasa is hiding somewhere.")
        println("\n[AI NARRATIVE 1]:\n$result0")

        println("\n--- ENGINE STATE ---")
        println("Time: ${worldState.timeString}, Location: ${ain.currentLocation.name}")

        // Turn 2: Leaving the room
        println("\n> Action: Seeing nobody around, Ain gets cleaned up and dressed...")
        val result1 = orchestrator.processTurn("Seeing nobody around, Ain gets cleaned up and dressed, pocketing his scalpel and notepad and quill, and walks out of the room.")
        println("\n[AI NARRATIVE 2]:\n$result1")

        println("\n--- ENGINE STATE ---")
        println("Time: ${worldState.timeString}, Location: ${ain.currentLocation.name}")

        // Turn 3: Entering the Grand Hall
        println("\n> Action: I make my way to the grand hall...")
        val result2 = orchestrator.processTurn("I travel to the grand hall, where my uncle, Duke Alistair, and my cousin Seraphina are eating.")
        println("\n[AI NARRATIVE 3]:\n$result2")

        // Final Status Check
        println("\n--- FINAL WORLD STATE ---")
        println("Current Time: ${worldState.timeString}")
        println("Ain's Position: ${ain.currentLocation.name}")
        println("Ain's Wallet: ${ain.wallet}")
    }
}