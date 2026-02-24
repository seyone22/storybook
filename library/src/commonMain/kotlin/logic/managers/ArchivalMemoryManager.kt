package io.github.kotlin.fibonacci.logic.managers

import io.github.kotlin.fibonacci.logic.ai.AiClient

class ArchivalMemoryManager(
    private val aiClient: AiClient,
    private val shortTermLimit: Int = 4 // How many exact turns to keep verbatim
) : MemoryManager {

    private val shortTermMemory = mutableListOf<String>()
    private var longTermSummary = "The story has just begun."

    override suspend fun addMemory(text: String) {
        shortTermMemory.add(text)

        // The Sliding Window Logic
        if (shortTermMemory.size > shortTermLimit) {
            // 1. Take the 2 oldest turns
            val eventsToCompress = shortTermMemory.take(2).joinToString("\n")

            // 2. WHERE WE USE THE FUNCTION: Ask the AI to fold them into the Long Term Summary
            longTermSummary = aiClient.summarizeEvents(longTermSummary, eventsToCompress)

            // 3. Remove those 2 turns from the Short Term window to save context tokens!
            shortTermMemory.subList(0, 2).clear()
            println("[SYSTEM]: Memory compressed to save tokens.")
        }
    }

    override suspend fun getRecentHistory(): String {
        return """
            [ARCHIVED HISTORY]:
            $longTermSummary
            
            [RECENT EVENTS]:
            ${shortTermMemory.joinToString("\n\n")}
        """.trimIndent()
    }

    override suspend fun clear() {
        shortTermMemory.clear()
        longTermSummary = "The story has just begun."
    }
}