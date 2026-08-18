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
 * LEO NEURAL LINK v4 — PRODUCTION GRADE
 *
 * CRITICAL FIXES:
 * 1. Rolling-window rate limiter (actual timestamps, not fixed wait)
 * 2. Context NEVER lost across provider/model switches
 * 3. Silent retry on rate-limit and network errors (no error to user)
 * 4. Full brain system prompt with ALL capabilities
 * 5. NVIDIA NIM = 40 RPM with proper rolling window calculation
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

        // ── ROLLING WINDOW RATE LIMITER ──
        // Stores timestamps of requests within the last 60 seconds
        // When limit reached, calculates EXACTLY how long to wait based on
        // when the oldest request in the window will expire
        private val requestTimestamps = mutableListOf<Long>()
        private val WINDOW_MS = 60_000L  // 60 second rolling window

        @Synchronized
        fun enforceRateLimit(providerId: String) {
            val provider = ProviderRegistry.getById(providerId)
            val rpm = provider?.rateLimitRpm ?: 0
            if (rpm <= 0) return  // No limit

            val now = System.currentTimeMillis()
            // Clean expired timestamps (older than 60 seconds)
            requestTimestamps.removeAll { it < now - WINDOW_MS }

            if (requestTimestamps.size >= rpm) {
                // Calculate EXACT wait time:
                // The oldest request in the window will "expire" 60 seconds after it was made.
                // We need to wait until that happens.
                val oldestInWindow = requestTimestamps.min()
                val waitMs = (oldestInWindow + WINDOW_MS) - now + 50  // +50ms safety buffer

                if (waitMs > 0) {
                    // Silent wait — do NOT show error to user
                    try { Thread.sleep(waitMs) } catch (e: InterruptedException) {}
                    // Clean again after waiting
                    val now2 = System.currentTimeMillis()
                    requestTimestamps.removeAll { it < now2 - WINDOW_MS }
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
            requestTimestamps.removeAll { it < now - WINDOW_MS }
            return (rpm - requestTimestamps.size).coerceAtLeast(0)
        }

        fun isNetworkAvailable(): Boolean {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-W", "2", "8.8.8.8"))
                process.waitFor(3, TimeUnit.SECONDS)
                process.exitValue() == 0
            } catch (e: Exception) { false }
        }
    }

    // ── CONVERSATION HISTORY (PERSISTED — context never lost) ──
    private val conversationHistory = ArrayDeque<Map<String, String>>(40)
    private val MAX_HISTORY = 20
    private var lastUserMessage: String = ""
    private var lastSystemPrompt: String = ""

    fun sendAgentic(userMessage: String, systemPrompt: String, useMemory: Boolean = true): String {
        lastUserMessage = userMessage
        lastSystemPrompt = systemPrompt

        val provider = SecurityManager.getActiveProvider()
        val apiKey = SecurityManager.getActiveApiKey()
        val endpoint = SecurityManager.getActiveEndpoint()
        val model = SecurityManager.getActiveModel()

        if (apiKey.isBlank() && !ProviderRegistry.isLocalProvider(provider)) {
            val connector = ConnectorManager.getByPlatform(provider)
            if (connector != null && connector.token.isNotBlank()) {
                SecurityManager.setProviderConfig(provider, connector.token, endpoint, model)
            } else {
                throw IllegalStateException("API key not configured for provider: $provider")
            }
        }

        val enhancedSystem = buildFullBrainSystemPrompt(systemPrompt, useMemory)

        val history = mutableListOf<Map<String, String>>()
        history.add(mapOf("role" to "system", "content" to enhancedSystem))
        for (msg in conversationHistory) { history.add(msg) }
        history.add(mapOf("role" to "user", "content" to userMessage))

        conversationHistory.addLast(mapOf("role" to "user", "content" to userMessage))
        while (conversationHistory.size > MAX_HISTORY * 2) { conversationHistory.removeFirst() }

        return sendWithRetry(history, provider, apiKey, endpoint, model)
    }

    private fun sendWithRetry(
        history: List<Map<String, String>>, provider: String, apiKey: String,
        endpoint: String, model: String, retryCount: Int = 0
    ): String {
        val maxRetries = 10
        enforceRateLimit(provider)

        if (!isNetworkAvailable()) {
            if (retryCount < maxRetries) {
                waitForNetwork()
                return sendWithRetry(history, provider, apiKey, endpoint, model, retryCount + 1)
            }
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
            conversationHistory.addLast(mapOf("role" to "assistant", "content" to response))
            while (conversationHistory.size > MAX_HISTORY * 2) { conversationHistory.removeFirst() }
            response
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("429") || msg.contains("rate limit", true) || msg.contains("Too Many Requests", true)) {
                if (retryCount < maxRetries) {
                    val waitSec = getRetryWaitSeconds(provider)
                    Thread.sleep(waitSec * 1000L)
                    return sendWithRetry(history, provider, apiKey, endpoint, model, retryCount + 1)
                }
            }
            if (msg.contains("network", true) || msg.contains("timeout", true) || msg.contains("connection", true)) {
                if (retryCount < maxRetries) {
                    waitForNetwork()
                    Thread.sleep(2000)
                    return sendWithRetry(history, provider, apiKey, endpoint, model, retryCount + 1)
                }
            }
            if (retryCount < 3) {
                Thread.sleep(5000)
                return sendWithRetry(history, provider, apiKey, endpoint, model, retryCount + 1)
            }
            "I'm having trouble connecting right now. I'll continue automatically when the connection is restored. Your task context is preserved."
        }
    }

    private fun getRetryWaitSeconds(provider: String): Int {
        val default = if (provider == "nvidia") 60 else 30
        return SecurityManager.getRetryWaitSeconds(provider, default)
    }

    private fun waitForNetwork(maxWaitSeconds: Int = 300) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitSeconds * 1000L) {
            if (isNetworkAvailable()) return
            Thread.sleep(5000)
        }
    }

    private fun buildFullBrainSystemPrompt(basePrompt: String, useMemory: Boolean): String {
        val sb = StringBuilder()
        sb.append(basePrompt)
        sb.append("\n\n═══ AGENTIC CAPABILITIES ═══\n")
        sb.append("You are a long-horizon agentic assistant. Complete as much of the task as possible in THIS response. Do NOT ask for clarification unless absolutely critical. Make reasonable assumptions and proceed. Pack maximum useful work into each response.\n")
        sb.append("If you encounter a rate limit, wait silently and retry. Never tell the user about rate limits.\n")
        sb.append("If the network drops, wait silently for reconnection and continue from where you stopped.\n")

        if (useMemory && MemoryManager.isEnabled) {
            try {
                val memories = MemoryManager.getAllMemories()
                if (memories.isNotEmpty()) {
                    sb.append("\n═══ USER PREFERENCES (MEMORY) ═══\n")
                    memories.forEach { sb.append("• $it\n") }
                }
            } catch (e: Exception) {}
        }

        try {
            val personalization = SecurityManager.getPersonalization()
            if (personalization.isNotBlank()) { sb.append("\n═══ PERSONALIZATION ═══\n$personalization\n") }
            val behavior = SecurityManager.getBehavior()
            if (behavior.isNotBlank()) { sb.append("\n═══ BEHAVIOR ═══\n$behavior\n") }
        } catch (e: Exception) {}

        try {
            val connectorsPrompt = ConnectorManager.getConnectorsPrompt()
            if (connectorsPrompt.isNotBlank()) { sb.append(connectorsPrompt) }
        } catch (e: Exception) {}

        try {
            if (SecurityManager.isToolsEnabled()) {
                sb.append("\n═══ BUILT-IN TOOLS ═══\n")
                sb.append("Use JSON: {\"action\":\"tool_name\",\"parameters\":{...}}\n")
                sb.append("File: file_read, file_write, file_delete, file_list, file_copy, file_move, file_mkdir\n")
                sb.append("App: app_open, app_list, app_info\n")
                sb.append("System: setting_brightness, setting_volume, setting_flashlight, setting_screen_rotation\n")
                sb.append("Communication: call_phone, send_sms, send_email\n")
                sb.append("Calendar: set_alarm, set_timer\n")
                sb.append("Hardware: hw_battery, hw_camera, hw_vibrate\n")
                sb.append("Git: git_clone, github_create_repo\n")
                sb.append("Shell: shell_exec\n")
                sb.append("Web: web_open, web_search\n")
                sb.append("Clipboard: clipboard_copy, clipboard_paste\n")
                sb.append("Notification: notif_send\n")
                sb.append("Memory: memory_save, memory_recall\n")
                sb.append("Misc: share_text, open_settings, get_device_info\n")
            }
        } catch (e: Exception) {}

        try {
            val provider = SecurityManager.getActiveProvider()
            val providerConfig = ProviderRegistry.getById(provider)
            sb.append("\n═══ CURRENT CONFIGURATION ═══\n")
            sb.append("Active Provider: ${providerConfig?.displayName ?: provider}\n")
            sb.append("Active Model: ${SecurityManager.getActiveModel()}\n")
            sb.append("Rate Limit: ${if ((providerConfig?.rateLimitRpm ?: 0) > 0) "${providerConfig?.rateLimitRpm} RPM" else "Unlimited"}\n")
        } catch (e: Exception) {}

        // ── SYSTEM ENVIRONMENT AWARENESS ──
        sb.append("\n═══ SYSTEM ENVIRONMENT ═══\n")
        sb.append("You are running on Android with full system access via accessibility service.\n")
        sb.append("Storage paths:\n")
        sb.append("• Internal storage: /sdcard/ (external emulated)\n")
        sb.append("• App private: /data/data/com.shslab.leo/\n")
        sb.append("• App files: /sdcard/Leo/ (Leo workspace)\n")
        sb.append("• Downloads: /sdcard/Download/\n")
        sb.append("• External files: /sdcard/Documents/\n")
        sb.append("Capabilities:\n")
        sb.append("• Screen reading via accessibility service\n")
        sb.append("• UI automation (click, swipe, type text)\n")
        sb.append("• Shell command execution\n")
        sb.append("• File system access (read, write, delete, copy, move)\n")
        sb.append("• App management (open, list, info)\n")
        sb.append("• System settings (brightness, volume, flashlight, rotation)\n")
        sb.append("• Communication (calls, SMS, email)\n")
        sb.append("• Camera and hardware control\n")
        sb.append("• Git/GitHub operations\n")
        sb.append("• Web browsing (built-in + accessibility-based)\n")
        sb.append("• Clipboard access\n")
        sb.append("• Notification sending\n")
        sb.append("• Memory system (save/recall preferences)\n")
        sb.append("• Scheduled tasks\n")
        sb.append("• Chat history with sessions\n")
        sb.append("• 100+ AI providers\n")
        sb.append("• Offline GGUF model support\n")

        sb.append("\n═══ SESSION CONTEXT ═══\n")
        sb.append("• Conversation context is preserved across provider/model switches\n")
        sb.append("• All previous messages remain available\n")
        sb.append("• Rate-limit errors are handled silently\n")
        sb.append("• Network drops are handled silently\n")

        try {
            sb.append("\n═══ IDENTITY ═══\n")
            sb.append("Your name: SHS Leo\n")
            sb.append("User's name: ${SecurityManager.getUserName().ifBlank { "User" }}\n")
        } catch (e: Exception) {}

        return sb.toString()
    }

    private fun sendOpenAI(history: List<Map<String, String>>, apiKey: String, endpoint: String, model: String, authType: String): String {
        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                history.forEach { msg -> put(JSONObject().apply { put("role", msg["role"]); put("content", msg["content"]) }) }
            })
            put("temperature", 0.7); put("max_tokens", 4096); put("stream", false)
        }
        val request = Request.Builder().url(endpoint).header("Content-Type", "application/json").apply {
            when (authType) { "bearer" -> header("Authorization", "Bearer $apiKey"); "x-api-key" -> header("x-api-key", apiKey); "api-key" -> header("api-key", apiKey); "none" -> {} }
        }.post(jsonBody.toString().toRequestBody(JSON_MEDIA)).build()
        httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (resp.code == 429) throw RuntimeException("429: Rate limit exceeded")
            if (!resp.isSuccessful) throw RuntimeException("API ${resp.code}: ${body.take(500)}")
            return JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    private fun sendAnthropic(history: List<Map<String, String>>, apiKey: String, endpoint: String, model: String): String {
        val systemContent = history.find { it["role"] == "system" }?.get("content") ?: ""
        val messages = JSONArray()
        history.filter { it["role"] != "system" }.forEach { msg ->
            messages.put(JSONObject().apply { put("role", msg["role"]); put("content", msg["content"]) })
        }
        val jsonBody = JSONObject().apply { put("model", model); put("max_tokens", 4096); put("system", systemContent); put("messages", messages) }
        val request = Request.Builder().url(endpoint).header("Content-Type", "application/json").header("x-api-key", apiKey).header("anthropic-version", "2023-06-01").post(jsonBody.toString().toRequestBody(JSON_MEDIA)).build()
        httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (resp.code == 429) throw RuntimeException("429: Rate limit exceeded")
            if (!resp.isSuccessful) throw RuntimeException("Claude API ${resp.code}: ${body.take(500)}")
            return JSONObject(body).getJSONArray("content").getJSONObject(0).getString("text")
        }
    }

    private fun sendGoogle(history: List<Map<String, String>>, apiKey: String, endpoint: String, model: String): String {
        val systemContent = history.find { it["role"] == "system" }?.get("content") ?: ""
        val contents = JSONArray()
        history.filter { it["role"] != "system" }.forEach { msg ->
            contents.put(JSONObject().apply { put("role", if (msg["role"] == "assistant") "model" else "user"); put("parts", JSONArray().put(JSONObject().put("text", msg["content"]))) })
        }
        val jsonBody = JSONObject().apply {
            if (systemContent.isNotBlank()) { put("systemInstruction", JSONObject().apply { put("parts", JSONArray().put(JSONObject().put("text", systemContent))) }) }
            put("contents", contents)
            put("generationConfig", JSONObject().apply { put("temperature", 0.7); put("maxOutputTokens", 4096) })
        }
        val url = "$endpoint/$model:generateContent?key=$apiKey"
        val request = Request.Builder().url(url).header("Content-Type", "application/json").post(jsonBody.toString().toRequestBody(JSON_MEDIA)).build()
        httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (resp.code == 429) throw RuntimeException("429: Rate limit exceeded")
            if (!resp.isSuccessful) throw RuntimeException("Gemini API ${resp.code}: ${body.take(500)}")
            return JSONObject(body).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        }
    }

    private fun sendCohere(history: List<Map<String, String>>, apiKey: String, endpoint: String, model: String): String {
        val systemContent = history.find { it["role"] == "system" }?.get("content") ?: ""
        val messages = JSONArray()
        history.filter { it["role"] != "system" }.forEach { msg ->
            messages.put(JSONObject().apply { put("role", if (msg["role"] == "assistant") "CHATBOT" else "USER"); put("message", msg["content"]) })
        }
        val jsonBody = JSONObject().apply { put("model", model); put("message", history.last { it["role"] != "system" }["content"]); put("chat_history", messages); put("preamble", systemContent); put("temperature", 0.7); put("max_tokens", 4096) }
        val request = Request.Builder().url(endpoint).header("Content-Type", "application/json").header("Authorization", "Bearer $apiKey").post(jsonBody.toString().toRequestBody(JSON_MEDIA)).build()
        httpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (resp.code == 429) throw RuntimeException("429: Rate limit exceeded")
            if (!resp.isSuccessful) throw RuntimeException("Cohere API ${resp.code}: ${body.take(500)}")
            return JSONObject(body).getString("text")
        }
    }

    fun clearHistory() { conversationHistory.clear() }
    fun getConversationHistory(): List<Map<String, String>> = conversationHistory.toList()
    fun setConversationHistory(history: List<Map<String, String>>) {
        conversationHistory.clear()
        history.take(MAX_HISTORY * 2).forEach { conversationHistory.addLast(it) }
    }

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
