package com.shslab.leo.connectors

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * LEO CONNECTORS — Platform Credential Manager
 * Stores user-defined platform connections (GitHub, GitLab, Jira, custom, etc.)
 * No default connectors — user adds their own via "Add Connector".
 */
object ConnectorManager {

    private const val PREFS = "leo_connectors"
    private const val KEY_CONNECTORS = "connectors_list"

    data class Connector(
        val id: String,
        val platformName: String,
        val username: String,
        val email: String,
        val token: String,
        val createdAt: Long = System.currentTimeMillis()
    )

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun getAllConnectors(): List<Connector> {
        val json = prefs?.getString(KEY_CONNECTORS, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<Connector>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(Connector(
                id = obj.getString("id"),
                platformName = obj.getString("platformName"),
                username = obj.getString("username"),
                email = obj.getString("email"),
                token = obj.getString("token"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            ))
        }
        return list
    }

    fun addConnector(platformName: String, username: String, email: String, token: String): String {
        val id = java.util.UUID.randomUUID().toString()
        val connector = Connector(id, platformName, username, email, token)
        val connectors = getAllConnectors().toMutableList()
        connectors.add(connector)
        saveConnectors(connectors)
        return id
    }

    fun updateConnector(id: String, platformName: String, username: String, email: String, token: String) {
        val connectors = getAllConnectors().toMutableList()
        val idx = connectors.indexOfFirst { it.id == id }
        if (idx >= 0) {
            connectors[idx] = connectors[idx].copy(platformName = platformName, username = username, email = email, token = token)
            saveConnectors(connectors)
        }
    }

    fun deleteConnector(id: String) {
        val connectors = getAllConnectors().filter { it.id != id }
        saveConnectors(connectors)
    }

    fun getByPlatform(platformName: String): Connector? =
        getAllConnectors().find { it.platformName.equals(platformName, ignoreCase = true) }

    fun getById(id: String): Connector? = getAllConnectors().find { it.id == id }

    private fun saveConnectors(connectors: List<Connector>) {
        val arr = JSONArray()
        for (c in connectors) {
            arr.put(JSONObject().apply {
                put("id", c.id); put("platformName", c.platformName)
                put("username", c.username); put("email", c.email)
                put("token", c.token); put("createdAt", c.createdAt)
            })
        }
        prefs?.edit()?.putString(KEY_CONNECTORS, arr.toString())?.apply()
    }

    fun getConnectorsPrompt(): String {
        val connectors = getAllConnectors()
        if (connectors.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("\n═══ CONNECTORS (Platform Credentials) ═══\n")
        sb.append("You have access to the following platform connections:\n")
        for (c in connectors) {
            sb.append("• ${c.platformName}: user=${c.username}, email=${c.email}, token=✓\n")
        }
        sb.append("Use these credentials when interacting with these platforms.\n")
        return sb.toString()
    }
}
