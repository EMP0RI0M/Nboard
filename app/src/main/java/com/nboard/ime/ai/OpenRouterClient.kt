package com.nboard.ime.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenRouterClient(private val apiKey: String) : AiClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    override suspend fun generateText(
        prompt: String,
        systemInstruction: String?,
        outputCharLimit: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("OpenRouter API key missing"))
        }

        var lastNotFoundError: Exception? = null
        MODEL_FALLBACKS.forEach { model ->
            val result = generateWithModel(prompt, model, systemInstruction, outputCharLimit)
            if (result.isSuccess) {
                return@withContext result
            }

            val failure = result.exceptionOrNull()
            if (failure is OpenRouterHttpException && failure.httpCode == 404) {
                lastNotFoundError = failure
            } else {
                return@withContext result
            }
        }

        Result.failure(lastNotFoundError ?: IOException("No compatible OpenRouter model available"))
    }

    private fun generateWithModel(
        prompt: String,
        model: String,
        systemInstruction: String?,
        outputCharLimit: Int
    ): Result<String> {
        val url = "https://openrouter.ai/api/v1/chat/completions"
        
        val messages = JSONArray()
        if (!systemInstruction.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction.trim()))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))

        val requestJson = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.3)
            .put("max_tokens", 256)

        val requestBody = requestJson
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/MathieuDvv/Nboard")
            .header("X-Title", "Nboard")
            .post(requestBody)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorMessage = extractApiErrorMessage(bodyString)
                    return Result.failure(
                        OpenRouterHttpException(
                            httpCode = response.code,
                            detail = errorMessage ?: "OpenRouter request failed (${response.code})"
                        )
                    )
                }
                if (bodyString.isBlank()) {
                    return Result.failure(IOException("OpenRouter returned an empty response"))
                }

                val json = JSONObject(bodyString)
                var text = extractCandidateText(json)
                if (outputCharLimit > 0 && text.length > outputCharLimit) {
                    text = text.take(outputCharLimit).trimEnd().plus("…")
                }
                if (text.isNotBlank()) {
                    return Result.success(text)
                }

                Result.failure(IOException("OpenRouter response had no text output"))
            }
        } catch (error: Exception) {
            Result.failure(IOException(error.message ?: "OpenRouter request error", error))
        }
    }

    private fun extractCandidateText(json: JSONObject): String {
        val choices = json.optJSONArray("choices") ?: return ""
        if (choices.length() > 0) {
            return choices.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty().trim()
        }
        return ""
    }

    private fun extractApiErrorMessage(body: String): String? {
        if (body.isBlank()) {
            return null
        }
        return runCatching {
            JSONObject(body)
                .optJSONObject("error")
                ?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private data class OpenRouterHttpException(
        val httpCode: Int,
        val detail: String
    ) : IOException(detail)

    companion object {
        private val MODEL_FALLBACKS = listOf(
            "google/gemini-2.5-flash",
            "google/gemini-2.0-flash-001",
            "openai/gpt-4o-mini"
        )
    }
}
