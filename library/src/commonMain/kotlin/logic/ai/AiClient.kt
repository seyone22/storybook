package io.github.kotlin.fibonacci.logic.ai

import io.github.kotlin.fibonacci.domain.models.*

interface AiClient {
    suspend fun generatePrologue(worldState: WorldState, mainCharacter: Character, context: String, worldConfig: WorldConfig): String
    suspend fun getDirectorPlaybook(userInput: String, worldState: WorldState, mainCharacter: Character, globalTruth: String, storySoFar: String): DirectorPlaybook
    suspend fun generateActorPerformance(characterName: String, context: String, userInput: String, storySoFar: String): String
    suspend fun composeFinalNarrative(userInput: String, performances: List<String>, systemNotes: String, worldState: WorldState, storySoFar: String, worldConfig: WorldConfig): String
}