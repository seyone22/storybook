package io.github.kotlin.fibonacci.logic.managers

/**
 * A volatile, in-memory implementation for the V1 prototype.
 * Data will be lost when the application closes.
 */
class TransientMemoryManager(
    private val maxHistoryItems: Int = 3
) : MemoryManager {

    private val recentHistory = mutableListOf<String>()

    override suspend fun addMemory(prose: String) {
        recentHistory.add(prose)
        // Keep the token count manageable by only remembering the last N turns
        if (recentHistory.size > maxHistoryItems) {
            recentHistory.removeAt(0)
        }
    }

    override suspend fun getRecentHistory(): String {
        return if (recentHistory.isEmpty()) {
            "The story has just begun."
        } else {
            recentHistory.joinToString("\n\n")
        }
    }

    override suspend fun clear() {
        recentHistory.clear()
    }
}