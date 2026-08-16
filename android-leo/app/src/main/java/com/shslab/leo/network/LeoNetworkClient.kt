package com.shslab.leo.network

import com.shslab.leo.core.LeoProtocol
import com.shslab.leo.core.Logger
import com.shslab.leo.memory.MemoryManager
import com.shslab.leo.persona.DoraemonPersona
import com.shslab.leo.security.SecurityManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * ═══════════════════════════════════════════════════════════════
 *  LEO NEURAL LINK v2 — AGENTIC + RATE-LIMIT AWARE
 *
 *  Features:
 *  - 100+ providers via ProviderRegistry
 *  - Provider-specific rate limiting (NVIDIA 40 RPM, etc.)
 *  - Long-horizon task support (single request does max work)
 *  - GGUF/offline model support via local OpenAI-compatible API
 *  - Memory injection into system prompt
 *  ──────────────────────────────────────────────────────────────
 *  Rate Limit Strategy:
 *  - If provider has rateLimitRpm > 0, enforce minimum interval
 *  - For long-horizon tasks, pack max reasoning into each request
 *  - Avoid wasting requests — each call should do maximum work
 * ═══════════════════════════════════════════════════════════════
 */
class LeoNetworkClient {

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)   // 2 min for complex agentic reasoning
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectionPool(okhttp3.ConnectionPool(3, 60, TimeUnit.SECONDS))
                .retryOnConnectionFailure(true)
                .build()
        }

        // ── Rate Limit Tracking ──
        private val requestTimestamps = mutableListOf<Long>()
        private val MAX_HISTORY_MS = 60_000L  // 1 minute window

        /** Check and enforce rate limit before making a request */
        @Synchronized
        fun enforceRateLimit(providerId: String) {
            val provider = ProviderRegistry.getById(providerId)
            val rpm = provider?.rateLimitRpm ?: 0
            if (rpm <= 0) return  // No limit

            val now = System.currentTimeMillis()
            // Clean old timestamps
            requestTimestamps.removeAll { it < now - MAX_HISTORY_MS }

            if (requestTimestamps.size >= rpm) {
                // Calculate wait time
                val oldest = requestTimestamps.min()
                val waitMs = MAX_HISTORY_MS - (now - oldest) + 100  // +100ms buffer
                if (waitMs > 0) {
                    Logger.log("RateLimit", "Provider $providerId at ${rpm} RPM limit, waiting ${waitMs}ms")
                    Thread.sleep(waitMs)
                }
            }

            requestTimestamps.add(System.currentTimeMillis())
        }

        /** Get remaining requests in current window */
        @Synchronized
        fun getRemainingRequests(providerId: String): Int {
            val provider = ProviderRegistry.getById(providerId) ?: return Int.MAX_VALUE
            val rpm = provider.rateLimitRpm
            if (rpm <= 0) return Int.MAX_VALUE

            val now = System.currentTimeMillis()
            requestTimestamps.removeAll { it < now - MAX_HISTORY_MS }
            return (rpm - requestTimestamps.size).coerceAtLeast(0)
        }
    }

    private val conversationHistory = ArrayDeque<Map<String, String>>(20)
    private val MAX_HISTORY = 10

    /**
     * AGENTIC MODE: Send conversation with memory injection.
     * Builds a long-horizon system prompt that encourages completing
     * as much work as possible in a single request.
     */
    fun sendAgentic(
        userMessage: String,
        systemPrompt: String,
        useMemory: Boolean = true
    ): String {
        val provider = SecurityManager.getActiveProvider()
        val apiKey = SecurityManager.getActiveApiKey()
        val endpoint = SecurityManager.getActiveEndpoint()
        val model = SecurityManager.getActiveModel()

        if (apiKey.isBlank() && !ProviderRegistry.isLocalProvider(provider)) {
            throw IllegalStateException("API key not configured for provider: $provider")
        }

        // Enforce rate limit before request
        enforceRateLimit(provider)

        // Build enhanced system prompt with memory + agentic instructions
        val enhancedSystem = buildAgenticSystemPrompt(systemPrompt, useMemory)

        // Build conversation history
        val history = mutableListOf<Map<String, String>>()
        history.add(mapOf("role" to "system", "content" to enhancedSystem))

        // Add conversation history (bounded)
        for (msg in conversationHistory) {
            history.add(msg)
        }

        // Add current user message
        history.add(mapOf("role" to "user", "content" to userMessage))

        // Store in conversation history
        conversationHistory.addLast(mapOf("role" to "user", "content" to userMessage))
        while (conversationHistory.size > MAX_HISTORY * 2) {
            conversationHistory.removeFirst()
        }

        val providerConfig = ProviderRegistry.getById(provider)
        val apiFormat = providerConfig?.apiFormat ?: "openai"

        val response = when (apiFormat) {
            "anthropic" -> sendAnthropic(history, apiKey, endpoint, model)
            "google" -> sendGoogle(history, apiKey, endpoint, model)
            "cohere" -> sendCohere(history, apiKey, endpoint, model)
            else -> sendOpenAI(history, apiKey, endpoint, model, providerConfig?.authType ?: "bearer")
        }

        // Store response in history
        conversationHistory.addLast(mapOf("role" to "assistant", "content" to response))
        while (conversationHistory.size > MAX_HISTORY * 2) {
            conversationHistory.removeFirst()
        }

        return response
    }

    /**
     * Build an enhanced system prompt for agentic behavior.
     * Encourages long-horizon task completion.
     */
    private fun buildAgenticSystemPrompt(basePrompt: String, useMemory: Boolean): String {
        val sb = StringBuilder()

        sb.append(basePrompt)

        // Add agentic instructions
        sb.append("\n\n═══ AGENTIC INSTRUCTIONS ═══\n")
        sb.append("You are a long-horizon agentic assistant. ")
        sb.append("Complete as much of the task as possible in THIS response. ")
        sb.append("Do NOT ask for clarification unless absolutely critical. ")
        sb.append("Make reasonable assumptions and proceed. ")
        sb.append("Pack maximum useful work into each response.\n")

        // Inject memory if enabled
        if (useMemory) {
            try {
                val memory = MemoryManager.getAllMemories()
                if (memory.isNotEmpty()) {
                    sb.append("\n═══ USER PREFERENCES (MEMORY) ═══\n")
                    for (mem in memory) {
                        sb.append("• ").append(mem).append("\n")
                    }
                }
            } catch (e: Exception) {
                // Memory not available, skip
            }
        }

        // Inject personalization
        try {
            val personalization = SecurityManager.getPersonalization()
            if (personalization.isNotBlank()) {
                sb.append("\n═══ PERSONALIZATION ═══\n")
                sb.append(personalization).append("\n")
            }

            val behavior = SecurityManager.getBehavior()
            if (behavior.isNotBlank()) {
                sb.append("\n═══ BEHAVIOR ═══\n")
                sb.append(behavior).append("\n")
            }
        } catch (e: Exception) {}

        return sb.toString()
    }

    /** OpenAI-compatible API (most providers) */
    private fun sendOpenAI(
        history: List<Map<String, String>>,
        apiKey: String,
        endpoint: String,
        model: String,
        authType: String
    ): String {
        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                for (msg in history) {
                    put(JSONObject().apply {
                        put("role", msg["role"])
                        put("content", msg["content"])
                    })
                }
            })
            put("temperature", 0.7)
            put("max_tokens", 4096)
            put("stream", false)
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .apply {
                when (authType) {
                    "bearer" -> header("Authorization", "Bearer $apiKey")
                    "x-api-key" -> header("x-api-key", apiKey)
                    "api-key" -> header("api-key", apiKey)
                    "none" -> {} // No auth (local providers)
                }
            }
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("API ${resp.code}: ${body.take(500)}")
            }
            return parseOpenAIResponse(body)
        }
    }

    /** Anthropic Claude API */
    private fun sendAnthropic(
        history: List<Map<String, String>>,
        apiKey: String,
        endpoint: String,
        model: String
    ): String {
        // Extract system message
        val systemContent = history.find { it["role"] == "system" }?.get("content") ?: ""
        val messages = JSONArray()
        for (msg in history) {
            if (msg["role"] == "system") continue
            messages.put(JSONObject().apply {
                put("role", msg["role"])
                put("content", msg["content"])
            })
        }

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("max_tokens", 4096)
            put("system", systemContent)
            put("messages", messages)
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("Claude API ${resp.code}: ${body.take(500)}")
            }
            val json = JSONObject(body)
            return json.getJSONArray("content").getJSONObject(0).getString("text")
        }
    }

    /** Google Gemini API */
    private fun sendGoogle(
        history: List<Map<String, String>>,
        apiKey: String,
        endpoint: String,
        model: String
    ): String {
        val systemContent = history.find { it["role"] == "system" }?.get("content") ?: ""

        val contents = JSONArray()
        for (msg in history) {
            if (msg["role"] == "system") continue
            contents.put(JSONObject().apply {
                put("role", if (msg["role"] == "assistant") "model" else "user")
                put("parts", JSONArray().put(JSONObject().put("text", msg["content"])))
            })
        }

        val jsonBody = JSONObject().apply {
            if (systemContent.isNotBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemContent)))
                })
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 4096)
            })
        }

        val url = "$endpoint/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("Gemini API ${resp.code}: ${body.take(500)}")
            }
            val json = JSONObject(body)
            return json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }

    /** Cohere API */
    private fun sendCohere(
        history: List<Map<String, String>>,
        apiKey: String,
        endpoint: String,
        model: String
    ): String {
        val systemContent = history.find { it["role"] == "system" }?.get("content") ?: ""
        val messages = JSONArray()
        for (msg in history) {
            if (msg["role"] == "system") continue
            messages.put(JSONObject().apply {
                put("role", if (msg["role"] == "assistant" ) "CHATBOT" else "USER")
                put("message", msg["content"])
            })
        }

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("message", history.last { it["role"] != "system" }["content"])
            put("chat_history", messages)
            put("preamble", systemContent)
            put("temperature", 0.7)
            put("max_tokens", 4096)
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("Cohere API ${resp.code}: ${body.take(500)}")
            }
            return JSONObject(body).getString("text")
        }
    }

    private fun parseOpenAIResponse(body: String): String {
        val json = JSONObject(body)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    /** Clear conversation history */
    fun clearHistory() {
        conversationHistory.clear()
    }

    /** Legacy method for backward compatibility */
    fun sendWithHistory(history: List<Map<String, String>>): String {
        val apiKey = SecurityManager.getActiveApiKey()
        val endpoint = SecurityManager.getActiveEndpoint()
        val model = SecurityManager.getActiveModel()
        val provider = SecurityManager.getActiveProvider()
        val providerConfig = ProviderRegistry.getById(provider)
        val apiFormat = providerConfig?.apiFormat ?: "openai"

        enforceRateLimit(provider)

        return when (apiFormat) {
            "anthropic" -> sendAnthropic(history, apiKey, endpoint, model)
            "google" -> sendGoogle(history, apiKey, endpoint, model)
            "cohere" -> sendCohere(history, apiKey, endpoint, model)
            else -> sendOpenAI(history, apiKey, endpoint, model, providerConfig?.authType ?: "bearer")
        }
    }
}
