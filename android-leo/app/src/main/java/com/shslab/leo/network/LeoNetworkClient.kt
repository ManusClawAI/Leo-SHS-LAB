package com.shslab.leo.network

import com.shslab.leo.core.Logger
import com.shslab.leo.memory.MemoryManager
import com.shslab.leo.security.SecurityManager
import com.shslab.leo.connectors.ConnectorManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══════════════════════════════════════════════════════════════
 *  LEO NEURAL LINK v3 — AGENTIC + RESILIENT
 *
 *  Final 4 Requirements:
 *  1. Connectors: Platform credentials injected into context
 *  2. Rate-limit errors: Silent retry, no error to user, auto-continue
 *  3. Provider/model switching: Context preserved (conversation history kept)
 *  4. Full brain: System prompt knows ALL capabilities
 *
 *  Rate Limit Strategy:
 *  - On 429/rate-limit error: wait silently, retry automatically
 *  - Configurable wait time (default 60s for NVIDIA, user can change)
 *  - Never sends error message to user for rate limits
 *  - Continues from exactly where it stopped
 *
 *  Network Drop Strategy:
 *  - On network failure: wait for reconnection
 *  - Auto-retry when network returns
 *  - Continues from where it stopped
 * ═══════════════════════════════════════════════════════════════
 */
class LeoNetworkClient {

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectionPool(okhttp3.ConnectionPool(3, 60, TimeUnit.SECONDS))
                .retryOnConnectionFailure(true)
                .build()
        }

        // ── Rate Limit Tracking ──
        private val requestTimestamps = mutableListOf<Long>()
        private val MAX_HISTORY_MS = 60_000L

        @Synchronized
        fun enforceRateLimit(providerId: String) {
            val provider = ProviderRegistry.getById(providerId)
            val rpm = provider?.rateLimitRpm ?: 0
            if (rpm <= 0) return

            val now = System.currentTimeMillis()
            requestTimestamps.removeAll { it < now - MAX_HISTORY_MS }

            if (requestTimestamps.size >= rpm) {
                val oldest = requestTimestamps.min()
                val waitMs = MAX_HISTORY_MS - (now - oldest) + 100
                if (waitMs > 0) {
                    Logger.log("RateLimit", "Provider $providerId at limit, waiting ${waitMs}ms (silent)")
                    Thread.sleep(waitMs)
                }
            }
            requestTimestamps.add(System.currentTimeMillis())
        }

        @Synchronized
        fun getRemainingRequests(providerId: String): Int {
            val provider = ProviderRegistry.getById(providerId) ?: return Int.MAX_VALUE
            val rpm = provider.rateLimitRpm
            if (rpm <= 0) return Int.MAX_VALUE
            val now = System.currentTimeMillis()
            requestTimestamps.removeAll { it < now - MAX_HISTORY_MS }
            return (rpm - requestTimestamps.size).coerceAtLeast(0)
        }

        /** Check if network is available */
        fun isNetworkAvailable(): Boolean {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "8.8.8.8"))
                process.waitFor(3, TimeUnit.SECONDS)
                process.exitValue() == 0
            } catch (e: Exception) {
                false
            }
        }
    }

    // ── Conversation History (PERSISTED across provider/model switches) ──
    // This is the KEY for requirement 3: switching provider/model keeps context
    private val conversationHistory = ArrayDeque<Map<String, String>>(40)
    private val MAX_HISTORY = 20

    // ── Task State (for auto-continue after rate limit / network drop) ──
    private var lastUserMessage: String = ""
    private var lastSystemPrompt: String = ""
    private var isRetrying = AtomicBoolean(false)

    /**
     * AGENTIC SEND with full resilience.
     *
     * On rate-limit error:
     *   - Does NOT send error to user
     *   - Waits silently for configurable time
     *   - Retries automatically
     *   - Continues from exactly where it stopped
     *
     * On network drop:
     *   - Waits for network to return
     *   - Retries automatically
     *   - Continues from where it stopped
     */
    fun sendAgentic(
        userMessage: String,
        systemPrompt: String,
        useMemory: Boolean = true
    ): String {
        // Store for retry capability
        lastUserMessage = userMessage
        lastSystemPrompt = systemPrompt

        val provider = SecurityManager.getActiveProvider()
        val apiKey = SecurityManager.getActiveApiKey()
        val endpoint = SecurityManager.getActiveEndpoint()
        val model = SecurityManager.getActiveModel()

        if (apiKey.isBlank() && !ProviderRegistry.isLocalProvider(provider)) {
            // Try to find a connector with a token
            val connector = ConnectorManager.getByPlatform(provider)
            if (connector != null && connector.token.isNotBlank()) {
                SecurityManager.setProviderConfig(provider, connector.token, endpoint, model)
            } else {
                throw IllegalStateException("API key not configured for provider: $provider")
            }
        }

        // Build enhanced system prompt with FULL BRAIN (requirement 4)
        val enhancedSystem = buildFullBrainSystemPrompt(systemPrompt, useMemory)

        // Build conversation history (PRESERVES context across provider switches)
        val history = mutableListOf<Map<String, String>>()
        history.add(mapOf("role" to "system", "content" to enhancedSystem))
        for (msg in conversationHistory) {
            history.add(msg)
        }
        history.add(mapOf("role" to "user", "content" to userMessage))

        // Store user message in history
        conversationHistory.addLast(mapOf("role" to "user", "content" to userMessage))
        while (conversationHistory.size > MAX_HISTORY * 2) {
            conversationHistory.removeFirst()
        }

        // Send with retry logic (silent — no errors to user)
        return sendWithRetry(history, provider, apiKey, endpoint, model)
    }

    /**
     * Send with automatic retry on rate-limit or network error.
     * NEVER throws to user — always retries silently.
     */
    private fun sendWithRetry(
        history: List<Map<String, String>>,
        provider: String,
        apiKey: String,
        endpoint: String,
        model: String,
        retryCount: Int = 0
    ): String {
        val maxRetries = 10

        // Enforce rate limit before request
        enforceRateLimit(provider)

        // Check network before sending
        if (!isNetworkAvailable()) {
            Logger.log("Network", "Network down, waiting for reconnection (silent)")
            waitForNetwork()
            // After network returns, retry with SAME context
            return sendWithRetry(history, provider, apiKey, endpoint, model, retryCount + 1)
        }

        val providerConfig = ProviderRegistry.getById(provider)
        val apiFormat = providerConfig?.apiFormat ?: "openai"

        return try {
            val response = when (apiFormat) {
                "anthropic" -> sendAnthropic(history, apiKey, endpoint, model)
                "google" -> sendGoogle(history, apiKey, endpoint, model)
                "cohere" -> sendCohere(history, apiKey, endpoint, model)
                else -> sendOpenAI(history, apiKey, endpoint, model, providerConfig?.authType ?: "bearer")
            }

            // Store response in history (context preservation)
            conversationHistory.addLast(mapOf("role" to "assistant", "content" to response))
            while (conversationHistory.size > MAX_HISTORY * 2) {
                conversationHistory.removeFirst()
            }

            response
        } catch (e: Exception) {
            val msg = e.message ?: ""

            // Check if it's a rate-limit error (429)
            if (msg.contains("429") || msg.contains("rate limit", ignoreCase = true) ||
                msg.contains("Rate limit", ignoreCase = true) || msg.contains("Too Many Requests", ignoreCase = true)) {

                // SILENT retry — do NOT send error to user
                val waitSeconds = getRetryWaitSeconds(provider)
                Logger.log("RateLimit", "Rate limited, waiting ${waitSeconds}s silently (retry ${retryCount + 1}/$maxRetries)")

                if (retryCount < maxRetries) {
                    Thread.sleep(waitSeconds * 1000L)
                    // Retry with SAME context — continues from where it stopped
                    return sendWithRetry(history, provider, apiKey, endpoint, model, retryCount + 1)
                }
            }

            // Check if it's a network error
            if (msg.contains("network", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("connection", ignoreCase = true) ||
                msg.contains("ECONNRESET", ignoreCase = true) ||
                msg.contains("SocketException", ignoreCase = true)) {

                // SILENT retry — wait for network
                Logger.log("Network", "Network error, waiting for reconnection (silent, retry ${retryCount + 1}/$maxRetries)")

                if (retryCount < maxRetries) {
                    waitForNetwork()
                    Thread.sleep(2000) // small buffer after network returns
                    return sendWithRetry(history, provider, apiKey, endpoint, model, retryCount + 1)
                }
            }

            // For other errors, retry a few times silently
            if (retryCount < 3) {
                Logger.log("Retry", "Error: $msg — retrying silently (${retryCount + 1}/3)")
                Thread.sleep(5000)
                return sendWithRetry(history, provider, apiKey, endpoint, model, retryCount + 1)
            }

            // After all retries exhausted, return a graceful message (not an error)
            "I'm having trouble connecting right now. I'll continue automatically when the connection is restored. Your task context is preserved."
        }
    }

    /** Get configurable retry wait time per provider */
    private fun getRetryWaitSeconds(provider: String): Int {
        // Default: 60 seconds for NVIDIA (as specified)
        // User can configure via SecurityManager
        val defaultWait = if (provider == "nvidia") 60 else 30
        return SecurityManager.getRetryWaitSeconds(provider, defaultWait)
    }

    /** Wait for network to return (up to 5 minutes) */
    private fun waitForNetwork(maxWaitSeconds: Int = 300) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < maxWaitSeconds * 1000L) {
            if (isNetworkAvailable()) {
                Logger.log("Network", "Network restored after ${(System.currentTimeMillis() - startTime) / 1000}s")
                return
            }
            Thread.sleep(5000) // Check every 5 seconds
        }
        Logger.log("Network", "Network wait timeout after ${maxWaitSeconds}s")
    }

    /**
     * BUILD FULL BRAIN SYSTEM PROMPT (Requirement 4)
     * Leo knows about ALL capabilities: tools, connectors, memory, providers, etc.
     */
    private fun buildFullBrainSystemPrompt(basePrompt: String, useMemory: Boolean): String {
        val sb = StringBuilder()

        sb.append(basePrompt)

        // ── AGENTIC INSTRUCTIONS ──
        sb.append("\n\n═══ AGENTIC CAPABILITIES ═══\n")
        sb.append("You are a long-horizon agentic assistant. ")
        sb.append("Complete as much of the task as possible in THIS response. ")
        sb.append("Do NOT ask for clarification unless absolutely critical. ")
        sb.append("Make reasonable assumptions and proceed. ")
        sb.append("Pack maximum useful work into each response.\n")
        sb.append("If you encounter a rate limit, wait silently and retry — never tell the user about rate limits.\n")
        sb.append("If the network drops, wait silently for reconnection and continue from where you stopped.\n")

        // ── MEMORY ──
        if (useMemory && MemoryManager.isEnabled()) {
            try {
                val memory = MemoryManager.getAllMemories()
                if (memory.isNotEmpty()) {
                    sb.append("\n═══ USER PREFERENCES (MEMORY) ═══\n")
                    for (mem in memory) {
                        sb.append("• ").append(mem).append("\n")
                    }
                }
            } catch (e: Exception) {}
        }

        // ── PERSONALIZATION & BEHAVIOR ──
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

        // ── CONNECTORS ──
        try {
            val connectorsPrompt = ConnectorManager.getConnectorsPrompt()
            if (connectorsPrompt.isNotBlank()) {
                sb.append(connectorsPrompt)
            }
        } catch (e: Exception) {}

        // ── TOOLS ──
        try {
            if (SecurityManager.isToolsEnabled()) {
                // Import ToolRegistry dynamically to avoid circular dependency
                val toolsPrompt = com.shslab.leo.tools.ToolRegistryPrompt.getPrompt()
                if (toolsPrompt.isNotBlank()) {
                    sb.append("\n═══ BUILT-IN TOOLS ═══\n")
                    sb.append(toolsPrompt)
                }
            }
        } catch (e: Exception) {}

        // ── PROVIDER & MODEL AWARENESS ──
        try {
            val provider = SecurityManager.getActiveProvider()
            val providerConfig = ProviderRegistry.getById(provider)
            sb.append("\n═══ CURRENT CONFIGURATION ═══\n")
            sb.append("Active Provider: ${providerConfig?.displayName ?: provider}\n")
            sb.append("Active Model: ${SecurityManager.getActiveModel()}\n")
            sb.append("Rate Limit: ${if (providerConfig?.rateLimitRpm ?: 0 > 0) "${providerConfig.rateLimitRpm} RPM" else "Unlimited"}\n")
        } catch (e: Exception) {}

        // ── ANDROID CAPABILITIES ──
        sb.append("\n═══ ANDROID CAPABILITIES ═══\n")
        sb.append("You are running on an Android device with full system access:\n")
        sb.append("• File system access (read, write, delete, copy, move)\n")
        sb.append("• App management (open, install, uninstall, list)\n")
        sb.append("• System settings (brightness, volume, flashlight, WiFi, Bluetooth)\n")
        sb.append("• Communication (calls, SMS, email, contacts)\n")
        sb.append("• Calendar & alarms (set alarms, timers, create events)\n")
        sb.append("• Hardware (camera, vibrate, battery info, device info)\n")
        sb.append("• Shell commands (execute as root if available)\n")
        sb.append("• Git/GitHub (clone, commit, push, create repos)\n")
        sb.append("• Web (open URLs, search, fetch content)\n")
        sb.append("• Accessibility (click, swipe, type text, read screen)\n")
        sb.append("• Notifications (send, clear)\n")
        sb.append("• Clipboard (copy, paste)\n")
        sb.append("• Media control (play, pause, next, previous)\n")

        // ── ASSISTANT CAPABILITIES ──
        sb.append("\n═══ ASSISTANT CAPABILITIES ═══\n")
        sb.append("• Voice input via Android speech recognizer\n")
        sb.append("• Voice output via Android TextToSpeech (TTS)\n")
        sb.append("• Default Android Assistant (Siri-like activation)\n")
        sb.append("• Memory system (saves and recalls user preferences)\n")
        sb.append("• Scheduled tasks (execute tasks at specified time)\n")
        sb.append("• Chat history (sessions saved, searchable, pinnable)\n")
        sb.append("• Connectors (platform credentials for GitHub, etc.)\n")
        sb.append("• 100+ AI providers supported\n")
        sb.append("• Offline GGUF model support\n")

        // ── SESSION CONTEXT ──
        sb.append("\n═══ SESSION CONTEXT ═══\n")
        sb.append("• Conversation context is preserved when switching providers or models\n")
        sb.append("• All previous messages remain available after provider switch\n")
        sb.append("• Rate-limit errors are handled silently (no error to user)\n")
        sb.append("• Network drops are handled silently (auto-continue on reconnection)\n")

        // ── AGENT IDENTITY ──
        try {
            val agentName = SecurityManager.getAgentName()
            val userName = SecurityManager.getUserName()
            sb.append("\n═══ IDENTITY ═══\n")
            sb.append("Your name: $agentName\n")
            sb.append("User's name: ${userName.ifBlank { "User" }}\n")
        } catch (e: Exception) {}

        return sb.toString()
    }

    // ── API Format Implementations ──

    private fun sendOpenAI(history: List<Map<String, String>>, apiKey: String, endpoint: String, model: String, authType: String): String {
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
                    "none" -> {}
                }
            }
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (resp.code == 429) throw RuntimeException("429: Rate limit exceeded")
            if (!resp.isSuccessful) throw RuntimeException("API ${resp.code}: ${body.take(500)}")
            return parseOpenAIResponse(body)
        }
    }

    private fun sendAnthropic(history: List<Map<String, String>>, apiKey: String, endpoint: String, model: String): String {
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
            if (resp.code == 429) throw RuntimeException("429: Rate limit exceeded")
            if (!resp.isSuccessful) throw RuntimeException("Claude API ${resp.code}: ${body.take(500)}")
            val json = JSONObject(body)
            return json.getJSONArray("content").getJSONObject(0).getString("text")
        }
    }

    private fun sendGoogle(history: List<Map<String, String>>, apiKey: String, endpoint: String, model: String): String {
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
            if (resp.code == 429) throw RuntimeException("429: Rate limit exceeded")
            if (!resp.isSuccessful) throw RuntimeException("Gemini API ${resp.code}: ${body.take(500)}")
            val json = JSONObject(body)
            return json.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        }
    }

    private fun sendCohere(history: List<Map<String, String>>, apiKey: String, endpoint: String, model: String): String {
        val systemContent = history.find { it["role"] == "system" }?.get("content") ?: ""
        val messages = JSONArray()
        for (msg in history) {
            if (msg["role"] == "system") continue
            messages.put(JSONObject().apply {
                put("role", if (msg["role"] == "assistant") "CHATBOT" else "USER")
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
            if (resp.code == 429) throw RuntimeException("429: Rate limit exceeded")
            if (!resp.isSuccessful) throw RuntimeException("Cohere API ${resp.code}: ${body.take(500)}")
            return JSONObject(body).getString("text")
        }
    }

    private fun parseOpenAIResponse(body: String): String {
        val json = JSONObject(body)
        return json.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
    }

    /** Clear conversation history */
    fun clearHistory() {
        conversationHistory.clear()
    }

    /**
     * Get conversation history (for context preservation when switching providers).
     * Requirement 3: Switching provider/model must not lose context.
     */
    fun getConversationHistory(): List<Map<String, String>> {
        return conversationHistory.toList()
    }

    /**
     * Set conversation history (when loading a saved session).
     */
    fun setConversationHistory(history: List<Map<String, String>>) {
        conversationHistory.clear()
        for (msg in history.take(MAX_HISTORY * 2)) {
            conversationHistory.addLast(msg)
        }
    }

    /** Legacy method */
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
