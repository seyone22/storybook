package io.github.kotlin.fibonacci.logic.managers

/**
 * Handles the short-term and long-term narrative memory of the game.
 */
interface MemoryManager {
    /**
     * Saves a generated narrative paragraph to the memory store.
     */
    suspend fun addMemory(prose: String)

    /**
     * Retrieves the formatted narrative history to be used as context for the AI.
     */
    suspend fun getRecentHistory(): String

    /**
     * Clears the current memory (useful for resetting a session).
     */
    suspend fun clear()
}