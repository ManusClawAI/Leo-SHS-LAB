package com.shslab.leo.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.shslab.leo.network.ProviderRegistry

/**
 * ═══════════════════════════════════════════════════════════════
 *  LEO SECURITY VAULT v2 — Dynamic Provider Support
 *
 *  - 100+ providers via ProviderRegistry
 *  - Per-provider API key, endpoint, model
 *  - Custom providers (user-defined)
 *  - Personalization, Behavior, Memory settings
 *  - Theme, Color, Notification settings
 *  - GGUF model paths for offline use
 * ═══════════════════════════════════════════════════════════════
 */
object SecurityManager {

    private const val PREFS_FILE = "leo_vault"

    // ── Active Provider ──
    const val KEY_ACTIVE_PROVIDER   = "active_ai_provider"

    // ── Per-provider storage (dynamic) ──
    // Format: "provider_<id>_api_key", "provider_<id>_endpoint", "provider_<id>_model"
    private fun providerKey(providerId: String, field: String) = "provider_${providerId}_$field"

    // ── GitHub ──
    const val KEY_GITHUB_TOKEN      = "github_token"

    // ── Agent Identity ──
    private const val KEY_AGENT_NAME = "agent_name"
    private const val KEY_USER_NAME = "user_name"

    // ── Personalization & Behavior ──
    private const val KEY_PERSONALIZATION = "personalization"
    private const val KEY_BEHAVIOR = "behavior"

    // ── Memory ──
    private const val KEY_MEMORY_ENABLED = "memory_enabled"

    // ── Theme ──
    private const val KEY_THEME = "theme"  // "light", "dark", "system"
    private const val KEY_USER_MSG_COLOR = "user_msg_color"
    private const val KEY_AGENT_MSG_COLOR = "agent_msg_color"

    // ── General ──
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val KEY_TOOLS_ENABLED = "tools_enabled"

    // ── Notifications ──
    private const val KEY_NOTIF_MESSAGES = "notif_messages"
    private const val KEY_NOTIF_WORK = "notif_work"
    private const val KEY_NOTIF_TASKS = "notif_tasks"
    private const val KEY_NOTIF_REMINDERS = "notif_reminders"

    // ── Voice / TTS ──
    private const val KEY_VOICE_ENABLED = "voice_enabled"
    private const val KEY_TTS_ENABLED = "tts_enabled"

    // ── GGUF ──
    private const val KEY_GGUF_MODEL_PATH = "gguf_model_path"
    private const val KEY_GGUF_MODEL_NAME = "gguf_model_name"

    // ── Rate Limit Awareness ──
    private const val KEY_RATE_LIMIT_AWARE = "rate_limit_aware"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            prefs = EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            seedDefaults()
        }
    }

    private fun seedDefaults() {
        val p = prefs ?: return
        if (!p.contains(KEY_ACTIVE_PROVIDER))
            p.edit().putString(KEY_ACTIVE_PROVIDER, "openrouter").apply()
        if (!p.contains(KEY_AGENT_NAME))
            p.edit().putString(KEY_AGENT_NAME, "Leo").apply()
        if (!p.contains(KEY_THEME))
            p.edit().putString(KEY_THEME, "dark").apply()
        if (!p.contains(KEY_MEMORY_ENABLED))
            p.edit().putBoolean(KEY_MEMORY_ENABLED, true).apply()
        if (!p.contains(KEY_TOOLS_ENABLED))
            p.edit().putBoolean(KEY_TOOLS_ENABLED, true).apply()
        if (!p.contains(KEY_RATE_LIMIT_AWARE))
            p.edit().putBoolean(KEY_RATE_LIMIT_AWARE, true).apply()
        if (!p.contains(KEY_USER_MSG_COLOR))
            p.edit().putString(KEY_USER_MSG_COLOR, "#1A1A2E").apply()
        if (!p.contains(KEY_AGENT_MSG_COLOR))
            p.edit().putString(KEY_AGENT_MSG_COLOR, "#16213E").apply()
    }

    fun store(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    fun retrieve(key: String, default: String = ""): String {
        return prefs?.getString(key, default) ?: default
    }

    fun storeBool(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    fun retrieveBool(key: String, default: Boolean = false): Boolean {
        return prefs?.getBoolean(key, default) ?: default
    }

    fun delete(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    // ═══════════════════════════════════════════════════════════
    //  Active Provider
    // ═══════════════════════════════════════════════════════════

    fun getActiveProvider(): String = retrieve(KEY_ACTIVE_PROVIDER, "openrouter")

    fun setActiveProvider(providerId: String) {
        store(KEY_ACTIVE_PROVIDER, providerId)
    }

    fun getActiveApiKey(): String {
        val provider = getActiveProvider()
        // Check custom provider key first
        val key = retrieve(providerKey(provider, "api_key"), "")
        if (key.isNotBlank()) return key
        // Fall back to legacy keys
        return when (provider) {
            "gemini" -> retrieve("gemini_api_key", "")
            "claude", "anthropic" -> retrieve("claude_api_key", "")
            "openai" -> retrieve("openai_api_key", "")
            "openrouter" -> retrieve("openrouter_api_key", "")
            else -> retrieve(providerKey(provider, "api_key"), "")
        }
    }

    fun getActiveEndpoint(): String {
        val provider = getActiveProvider()
        val providerConfig = ProviderRegistry.getById(provider)
        val default = providerConfig?.endpoint ?: ""
        return retrieve(providerKey(provider, "endpoint"), default)
    }

    fun getActiveModel(): String {
        val provider = getActiveProvider()
        val providerConfig = ProviderRegistry.getById(provider)
        val default = providerConfig?.defaultModel ?: ""
        return retrieve(providerKey(provider, "model"), default)
    }

    // ═══════════════════════════════════════════════════════════
    //  Per-provider config
    // ═══════════════════════════════════════════════════════════

    fun setProviderConfig(providerId: String, apiKey: String, endpoint: String, model: String) {
        val editor = prefs?.edit()
        editor?.putString(providerKey(providerId, "api_key"), apiKey)
        editor?.putString(providerKey(providerId, "endpoint"), endpoint)
        editor?.putString(providerKey(providerId, "model"), model)
        editor?.apply()
    }

    fun getProviderApiKey(providerId: String): String = retrieve(providerKey(providerId, "api_key"), "")
    fun getProviderEndpoint(providerId: String): String {
        val config = ProviderRegistry.getById(providerId)
        return retrieve(providerKey(providerId, "endpoint"), config?.endpoint ?: "")
    }
    fun getProviderModel(providerId: String): String {
        val config = ProviderRegistry.getById(providerId)
        return retrieve(providerKey(providerId, "model"), config?.defaultModel ?: "")
    }

    // ═══════════════════════════════════════════════════════════
    //  Agent Identity
    // ═══════════════════════════════════════════════════════════

    fun setAgentName(name: String) {
        store(KEY_AGENT_NAME, name.trim().ifEmpty { "Leo" })
    }

    fun getAgentName(): String = retrieve(KEY_AGENT_NAME, "Leo")

    fun setUserName(name: String) {
        store(KEY_USER_NAME, name.trim())
    }

    fun getUserName(): String = retrieve(KEY_USER_NAME, "")

    // ═══════════════════════════════════════════════════════════
    //  Personalization & Behavior
    // ═══════════════════════════════════════════════════════════

    fun setPersonalization(text: String) {
        store(KEY_PERSONALIZATION, text)
    }

    fun getPersonalization(): String = retrieve(KEY_PERSONALIZATION, "")

    fun setBehavior(text: String) {
        store(KEY_BEHAVIOR, text)
    }

    fun getBehavior(): String = retrieve(KEY_BEHAVIOR, "")

    // ═══════════════════════════════════════════════════════════
    //  Memory
    // ═══════════════════════════════════════════════════════════

    fun setMemoryEnabled(enabled: Boolean) {
        storeBool(KEY_MEMORY_ENABLED, enabled)
    }

    fun isMemoryEnabled(): Boolean = retrieveBool(KEY_MEMORY_ENABLED, true)

    // ═══════════════════════════════════════════════════════════
    //  Theme
    // ═══════════════════════════════════════════════════════════

    fun setTheme(theme: String) {
        store(KEY_THEME, theme)
    }

    fun getTheme(): String = retrieve(KEY_THEME, "dark")

    fun setUserMsgColor(color: String) {
        store(KEY_USER_MSG_COLOR, color)
    }

    fun getUserMsgColor(): String = retrieve(KEY_USER_MSG_COLOR, "#1A1A2E")

    fun setAgentMsgColor(color: String) {
        store(KEY_AGENT_MSG_COLOR, color)
    }

    fun getAgentMsgColor(): String = retrieve(KEY_AGENT_MSG_COLOR, "#16213E")

    // ═══════════════════════════════════════════════════════════
    //  General
    // ═══════════════════════════════════════════════════════════

    fun setAppLanguage(lang: String) {
        store(KEY_APP_LANGUAGE, lang)
    }

    fun getAppLanguage(): String = retrieve(KEY_APP_LANGUAGE, "en")

    fun setToolsEnabled(enabled: Boolean) {
        storeBool(KEY_TOOLS_ENABLED, enabled)
    }

    fun isToolsEnabled(): Boolean = retrieveBool(KEY_TOOLS_ENABLED, true)

    // ═══════════════════════════════════════════════════════════
    //  Notifications
    // ═══════════════════════════════════════════════════════════

    fun setNotifMessages(enabled: Boolean) = storeBool(KEY_NOTIF_MESSAGES, enabled)
    fun isNotifMessagesEnabled(): Boolean = retrieveBool(KEY_NOTIF_MESSAGES, true)

    fun setNotifWork(enabled: Boolean) = storeBool(KEY_NOTIF_WORK, enabled)
    fun isNotifWorkEnabled(): Boolean = retrieveBool(KEY_NOTIF_WORK, true)

    fun setNotifTasks(enabled: Boolean) = storeBool(KEY_NOTIF_TASKS, enabled)
    fun isNotifTasksEnabled(): Boolean = retrieveBool(KEY_NOTIF_TASKS, true)

    fun setNotifReminders(enabled: Boolean) = storeBool(KEY_NOTIF_REMINDERS, enabled)
    fun isNotifRemindersEnabled(): Boolean = retrieveBool(KEY_NOTIF_REMINDERS, true)

    // ═══════════════════════════════════════════════════════════
    //  Voice / TTS
    // ═══════════════════════════════════════════════════════════

    fun setVoiceEnabled(enabled: Boolean) = storeBool(KEY_VOICE_ENABLED, enabled)
    fun isVoiceEnabled(): Boolean = retrieveBool(KEY_VOICE_ENABLED, true)

    fun setTTSEnabled(enabled: Boolean) = storeBool(KEY_TTS_ENABLED, enabled)
    fun isTTSEnabled(): Boolean = retrieveBool(KEY_TTS_ENABLED, false)

    // ═══════════════════════════════════════════════════════════
    //  GGUF (Offline Models)
    // ═══════════════════════════════════════════════════════════

    fun setGgufModelPath(path: String) {
        store(KEY_GGUF_MODEL_PATH, path)
    }

    fun getGgufModelPath(): String = retrieve(KEY_GGUF_MODEL_PATH, "")

    fun setGgufModelName(name: String) {
        store(KEY_GGUF_MODEL_NAME, name)
    }

    fun getGgufModelName(): String = retrieve(KEY_GGUF_MODEL_NAME, "")

    // ═══════════════════════════════════════════════════════════
    //  Rate Limit Awareness
    // ═══════════════════════════════════════════════════════════

    fun setRateLimitAware(enabled: Boolean) = storeBool(KEY_RATE_LIMIT_AWARE, enabled)
    fun isRateLimitAware(): Boolean = retrieveBool(KEY_RATE_LIMIT_AWARE, true)

    // ═══════════════════════════════════════════════════════════
    //  GitHub
    // ═══════════════════════════════════════════════════════════

    fun getGitHubToken(): String = retrieve(KEY_GITHUB_TOKEN)

    /** Wipe all vault data */
    fun nukeVault() {
        prefs?.edit()?.clear()?.apply()
    }
}
