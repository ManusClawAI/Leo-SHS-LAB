package com.shslab.leo.memory

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * ═══════════════════════════════════════════════════════════════
 *  LEO MEMORY MANAGER v2
 *
 *  - User preferences store
 *  - ON/OFF toggle
 *  - Manual add memories
 *  - Auto-save from phrases like "I like this", "remember this"
 *  - Inject into AI system prompt
 * ═══════════════════════════════════════════════════════════════
 */
object MemoryManager {

    private const val PREFS = "leo_memory_prefs"
    private const val KEY_MEMORIES = "memories_list"
    private const val KEY_ENABLED = "memory_enabled"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun isEnabled(): Boolean = prefs?.getBoolean(KEY_ENABLED, true) ?: true

    fun setEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
    }

    /** Get all saved memories */
    fun getAllMemories(): List<String> {
        val json = prefs?.getString(KEY_MEMORIES, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        return list
    }

    /** Add a memory manually */
    fun addMemory(text: String) {
        if (text.isBlank()) return
        val memories = getAllMemories().toMutableList()
        memories.add(text.trim())
        saveMemories(memories)
    }

    /** Remove a memory by index */
    fun removeMemory(index: Int) {
        val memories = getAllMemories().toMutableList()
        if (index in memories.indices) {
            memories.removeAt(index)
            saveMemories(memories)
        }
    }

    /** Clear all memories */
    fun clearAll() {
        prefs?.edit()?.putString(KEY_MEMORIES, "[]")?.apply()
    }

    /**
     * Auto-detect memory-worthy statements from user message.
     * Triggers on phrases like "I like", "remember", "I prefer", etc.
     * Returns true if a memory was saved.
     */
    fun autoExtractFromMessage(message: String): Boolean {
        if (!isEnabled()) return false
        val lower = message.lowercase().trim()

        val triggers = listOf(
            "i like ", "i prefer ", "i love ", "i hate ", "i dislike ",
            "remember ", "remember that ", "don't forget ", "note that ",
            "my name is ", "i am ", "i'm ", "always ", "never ",
            "i use ", "i work ", "i live ", "i speak ", "my favorite"
        )

        for (trigger in triggers) {
            if (lower.startsWith(trigger) || lower.contains(" " + trigger)) {
                // Extract the memory statement
                val idx = lower.indexOf(trigger)
                val memory = message.substring(idx).trim()
                // Don't save if too long or too short
                if (memory.length in 5..200) {
                    addMemory(memory)
                    return true
                }
            }
        }
        return false
    }

    private fun saveMemories(memories: List<String>) {
        val arr = JSONArray()
        for (m in memories) {
            arr.put(m)
        }
        prefs?.edit()?.putString(KEY_MEMORIES, arr.toString())?.apply()
    }

    /** Stats for logging */
    fun stats(): String {
        val count = getAllMemories().size
        return "Memory: $count memories, enabled=$isEnabled"
    }
}
