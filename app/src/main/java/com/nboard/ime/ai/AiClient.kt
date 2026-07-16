package com.nboard.ime.ai

interface AiClient {
    val isConfigured: Boolean
    suspend fun generateText(
        prompt: String,
        systemInstruction: String? = null,
        outputCharLimit: Int = 0
    ): Result<String>
}
