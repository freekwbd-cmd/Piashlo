package com.example.providers

import android.util.Log
import com.example.domain.model.AiModelSpec
import com.example.domain.model.ModelCatalog
import com.example.domain.model.ProviderType
import com.example.security.SecureKeyVault
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "AiProviderGateway"

/**
 * Modular AI Provider Gateway supporting:
 * 1. Google Gemini via Firebase AI Logic (`Firebase.ai` with App Check attestation)
 * 2. Anthropic Claude (`https://api.anthropic.com/v1/messages`)
 * 3. OpenAI (`https://api.openai.com/v1/chat/completions`)
 * 4. DeepSeek (`https://api.deepseek.com/v1/chat/completions`)
 * 5. OpenRouter (`https://openrouter.ai/api/v1/chat/completions`)
 * 6. Ollama (`http://<host>:11434/api/chat`)
 * 7. Compatible OpenAI API Endpoints
 */
class AiProviderGateway(
    private val keyVault: SecureKeyVault
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // Lazy initialization map for Firebase AI Logic Gemini models per skill mandate
    private val geminiModelsCache by lazy {
        ConcurrentHashMap<String, GenerativeModel>()
    }

    private fun getGeminiModel(modelName: String): GenerativeModel {
        val validName = when {
            modelName.startsWith("gemini-") -> modelName
            else -> "gemini-3.8-flash"
        }
        return geminiModelsCache.getOrPut(validName) {
            Firebase.ai.generativeModel(validName)
        }
    }

    /**
     * Streams tokens from the configured provider with a 30-60s timeout window.
     * Rethrows CancellationException to preserve cooperative coroutine cancellation.
     */
    fun streamCompletion(
        modelSpec: AiModelSpec,
        systemPrompt: String,
        userPrompt: String,
        timeoutMs: Long = 60_000L
    ): Flow<String> = flow {
        val fullPrompt = if (systemPrompt.isNotBlank()) {
            "$systemPrompt\n\n---\nUSER REQUEST:\n$userPrompt"
        } else {
            userPrompt
        }

        try {
            withTimeout(timeoutMs.coerceIn(30_000L, 60_000L)) {
                when (modelSpec.provider) {
                    ProviderType.GOOGLE_GEMINI -> {
                        val model = getGeminiModel(modelSpec.id)
                        val accumulated = StringBuilder()
                        model.generateContentStream(fullPrompt).collect { chunk ->
                            val delta = chunk.text.orEmpty()
                            if (delta.isNotEmpty()) {
                                accumulated.append(delta)
                                emit(accumulated.toString())
                            }
                        }
                        if (accumulated.isEmpty()) {
                            throw IllegalStateException("Gemini model returned an empty response.")
                        }
                    }
                    ProviderType.ANTHROPIC_CLAUDE -> {
                        val result = callAnthropicClaude(modelSpec, systemPrompt, userPrompt)
                        emitProgressiveChunks(result) { emit(it) }
                    }
                    ProviderType.OLLAMA -> {
                        val result = callOllama(modelSpec, systemPrompt, userPrompt)
                        emitProgressiveChunks(result) { emit(it) }
                    }
                    ProviderType.OPENAI,
                    ProviderType.DEEPSEEK,
                    ProviderType.OPENROUTER,
                    ProviderType.OPENAI_COMPATIBLE -> {
                        val result = callOpenAiCompatible(modelSpec, systemPrompt, userPrompt)
                        emitProgressiveChunks(result) { emit(it) }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out after ${timeoutMs}ms", e)
            throw IllegalStateException("Generation timed out after ${timeoutMs / 1000} seconds. Please retry or select a faster model.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Provider ${modelSpec.provider.displayName} inference failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Unary completion with 60s timeout for tool planning and sub-agents.
     */
    suspend fun generateUnary(
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        timeoutMs: Long = 60_000L
    ): String = withContext(Dispatchers.IO) {
        val spec = ModelCatalog.findById(modelId)
        val combinedPrompt = if (systemPrompt.isNotBlank()) {
            "$systemPrompt\n\n---\n$userPrompt"
        } else {
            userPrompt
        }

        try {
            withTimeout(timeoutMs.coerceIn(30_000L, 60_000L)) {
                when (spec.provider) {
                    ProviderType.GOOGLE_GEMINI -> {
                        val response = getGeminiModel(spec.id).generateContent(combinedPrompt)
                        val text = response.text
                        if (text.isNullOrBlank()) {
                            throw IllegalStateException("Gemini returned an empty response.")
                        }
                        text
                    }
                    ProviderType.ANTHROPIC_CLAUDE -> callAnthropicClaude(spec, systemPrompt, userPrompt)
                    ProviderType.OLLAMA -> callOllama(spec, systemPrompt, userPrompt)
                    else -> callOpenAiCompatible(spec, systemPrompt, userPrompt)
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Unary generation timed out", e)
            throw IllegalStateException("Generation timed out after ${timeoutMs / 1000} seconds.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unary generation failed on ${spec.displayName}", e)
            throw e
        }
    }

    /**
     * Tests connectivity and authentication for the given provider.
     */
    suspend fun testProviderConnection(provider: ProviderType): Result<String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val defaultModel = ModelCatalog.models.firstOrNull { it.provider == provider }
                ?: ModelCatalog.models.first()
            val reply = generateUnary(
                modelId = defaultModel.id,
                systemPrompt = "You are a connectivity diagnostic responder.",
                userPrompt = "Reply with the exact word ONLINE.",
                timeoutMs = 30_000L
            )
            val elapsed = System.currentTimeMillis() - start
            Result.success("${provider.displayName} connected (${elapsed}ms): ${reply.take(60).trim()}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Provider connection test failed for ${provider.name}", e)
            Result.failure(e)
        }
    }

    private suspend fun emitProgressiveChunks(fullText: String, emitter: suspend (String) -> Unit) {
        if (fullText.length <= 120) {
            emitter(fullText)
            return
        }
        val step = (fullText.length / 8).coerceAtLeast(80)
        var idx = step
        while (idx < fullText.length) {
            emitter(fullText.substring(0, idx))
            idx += step
        }
        emitter(fullText)
    }

    private fun callAnthropicClaude(
        spec: AiModelSpec,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val apiKey = keyVault.getApiKey(ProviderType.ANTHROPIC_CLAUDE)
        require(apiKey.isNotBlank()) {
            "Anthropic API key is not configured. Open Settings > AI Providers to store your key in Android Keystore, or switch to Google Gemini."
        }
        val baseUrl = keyVault.getEndpointUrl(ProviderType.ANTHROPIC_CLAUDE).trimEnd('/')
        val bodyJson = JSONObject().apply {
            put("model", spec.id)
            put("max_tokens", 4096)
            if (systemPrompt.isNotBlank()) {
                put("system", systemPrompt)
            }
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", userPrompt)
                )
            )
        }

        val req = Request.Builder()
            .url("$baseUrl/messages")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Anthropic HTTP ${resp.code}: ${raw.take(240)}")
            }
            val json = JSONObject(raw)
            val contentArr = json.optJSONArray("content") ?: JSONArray()
            val sb = StringBuilder()
            for (i in 0 until contentArr.length()) {
                val item = contentArr.optJSONObject(i)
                if (item?.optString("type") == "text") {
                    sb.append(item.optString("text"))
                }
            }
            return sb.toString().ifBlank { raw }
        }
    }

    private fun callOpenAiCompatible(
        spec: AiModelSpec,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val apiKey = keyVault.getApiKey(spec.provider)
        require(apiKey.isNotBlank()) {
            "${spec.provider.displayName} API key is not configured. Open Settings > AI Providers to save your key in Android Keystore, or switch to Google Gemini."
        }
        val baseUrl = keyVault.getEndpointUrl(spec.provider).trimEnd('/')
        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.put(JSONObject().put("role", "user").put("content", userPrompt))

        val bodyJson = JSONObject().apply {
            put("model", spec.id)
            put("messages", messages)
            put("temperature", 0.2)
        }

        val req = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("${spec.provider.displayName} HTTP ${resp.code}: ${raw.take(240)}")
            }
            val json = JSONObject(raw)
            val firstChoice = json.optJSONArray("choices")?.optJSONObject(0)
            return firstChoice?.optJSONObject("message")?.optString("content").orEmpty().ifBlank { raw }
        }
    }

    private fun callOllama(
        spec: AiModelSpec,
        systemPrompt: String,
        userPrompt: String
    ): String {
        val baseUrl = keyVault.getEndpointUrl(ProviderType.OLLAMA).trimEnd('/')
        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.put(JSONObject().put("role", "user").put("content", userPrompt))

        val bodyJson = JSONObject().apply {
            put("model", spec.id)
            put("messages", messages)
            put("stream", false)
        }

        val req = Request.Builder()
            .url("$baseUrl/chat")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Ollama HTTP ${resp.code}: ${raw.take(240)}")
            }
            val json = JSONObject(raw)
            return json.optJSONObject("message")?.optString("content").orEmpty().ifBlank { raw }
        }
    }

    companion object {
        fun estimateTokens(text: String): Int =
            ((text.length / 3.8).toInt()).coerceAtLeast(1)

        fun estimateCostUsd(modelSpec: AiModelSpec, inputTokens: Int, outputTokens: Int): Double {
            val inCost = (inputTokens / 1_000_000.0) * modelSpec.inputCostPerMillion
            val outCost = (outputTokens / 1_000_000.0) * modelSpec.outputCostPerMillion
            return inCost + outCost
        }
    }
}
