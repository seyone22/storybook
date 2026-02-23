package io.github.kotlin.fibonacci.logic.ai

interface AiClient {
    suspend fun generateNarrative(systemPrompt: String, userInput: String): String
}