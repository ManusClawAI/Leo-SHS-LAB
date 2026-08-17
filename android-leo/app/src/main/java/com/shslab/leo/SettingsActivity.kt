package com.shslab.leo

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shslab.leo.connectors.ConnectorManager
import com.shslab.leo.memory.MemoryManager
import com.shslab.leo.network.ProviderRegistry
import com.shslab.leo.security.SecurityManager

/**
 * Settings Activity — Profile/Main Settings
 * Sections: Personalization, Behavior, Memory, Theme, Color, General, Notifications, Archived Chats, Leo Settings
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var listView: RecyclerView
    private lateinit var adapter: SettingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)

        listView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
        }
        setContentView(listView)

        adapter = SettingsAdapter(buildSettings())
        listView.adapter = adapter
    }

    private fun applyTheme() {
        when (SecurityManager.getTheme()) {
            "light" -> setTheme(R.style.Theme_Leo_Light)
            else -> setTheme(R.style.Theme_Leo)
        }
    }

    private fun buildSettings(): List<SettingsItem> {
        val items = mutableListOf<SettingsItem>()

        // ── Personalization ──
        items.add(SettingsItem("Personalization", "Save your personal instructions") {
            showTextEdit("Personalization", SecurityManager.getPersonalization()) { text ->
                SecurityManager.setPersonalization(text)
            }
        })

        // ── Behavior ──
        items.add(SettingsItem("Behavior", "How Leo communicates with you") {
            showBehaviorDialog()
        })

        // ── Memory ──
        items.add(SettingsItem("Memory", if (MemoryManager.isEnabled) "ON — preferences saved" else "OFF") {
            MemoryManager.setEnabled(!MemoryManager.isEnabled)
            recreate()
        })
        items.add(SettingsItem("View Memories", "${MemoryManager.getAllMemories().size} memories saved") {
            showMemoriesDialog()
        })

        // ── Theme ──
        items.add(SettingsItem("Theme", "Current: ${SecurityManager.getTheme()}") {
            showThemeDialog()
        })

        // ── Color ──
        items.add(SettingsItem("User Message Color", SecurityManager.getUserMsgColor()) {
            showColorDialog("user")
        })
        items.add(SettingsItem("Agent Message Color", SecurityManager.getAgentMsgColor()) {
            showColorDialog("agent")
        })

        // ── General ──
        items.add(SettingsItem("App Language", "Current: ${SecurityManager.getAppLanguage()}") {
            showLanguageDialog()
        })
        items.add(SettingsItem("Tools", if (SecurityManager.isToolsEnabled()) "ON" else "OFF") {
            SecurityManager.setToolsEnabled(!SecurityManager.isToolsEnabled())
            recreate()
        })

        // ── Notifications ──
        items.add(SettingsItem("Notifications", "Configure notification settings") {
            showNotificationsDialog()
        })

        // ── Archived Chats ──
        items.add(SettingsItem("Archived Chats", "View and restore archived chats") {
            // Open archived chats
            Toast.makeText(this, "Archived chats", Toast.LENGTH_SHORT).show()
        })

        // ── Connectors ──
        items.add(SettingsItem("Connectors", "${ConnectorManager.getAllConnectors().size} platform connections") {
            showConnectorsDialog()
        })

        // ── Leo Settings ──
        items.add(SettingsItem("Provider", ProviderRegistry.getById(SecurityManager.getActiveProvider())?.displayName ?: "None") {
            showProviderDialog()
        })
        items.add(SettingsItem("API Key", if (SecurityManager.getActiveApiKey().isNotBlank()) "✓ Set" else "Not set") {
            showTextEdit("API Key", SecurityManager.getActiveApiKey()) { text ->
                SecurityManager.setProviderConfig(SecurityManager.getActiveProvider(), text,
                    SecurityManager.getActiveEndpoint(), SecurityManager.getActiveModel())
            }
        })
        items.add(SettingsItem("Model", SecurityManager.getActiveModel()) {
            showTextEdit("Model", SecurityManager.getActiveModel()) { text ->
                SecurityManager.setProviderConfig(SecurityManager.getActiveProvider(),
                    SecurityManager.getActiveApiKey(), SecurityManager.getActiveEndpoint(), text)
            }
        })
        items.add(SettingsItem("Base URL", SecurityManager.getActiveEndpoint()) {
            showTextEdit("Base URL", SecurityManager.getActiveEndpoint()) { text ->
                SecurityManager.setProviderConfig(SecurityManager.getActiveProvider(),
                    SecurityManager.getActiveApiKey(), text, SecurityManager.getActiveModel())
            }
        })
        items.add(SettingsItem("Upload GGUF Model", "For offline use") {
            Toast.makeText(this, "GGUF upload — select .gguf file", Toast.LENGTH_SHORT).show()
        })
        items.add(SettingsItem("Agent Name", SecurityManager.getAgentName()) {
            showTextEdit("Agent Name", SecurityManager.getAgentName()) { text ->
                SecurityManager.setAgentName(text)
            }
        })
        items.add(SettingsItem("Your Name", SecurityManager.getUserName()) {
            showTextEdit("Your Name", SecurityManager.getUserName()) { text ->
                SecurityManager.setUserName(text)
            }
        })

        // ── Rate Limit Retry Wait Time ──
        val activeProvider = SecurityManager.getActiveProvider()
        val defaultWait = if (activeProvider == "nvidia") 60 else 30
        val currentWait = SecurityManager.getRetryWaitSeconds(activeProvider, defaultWait)
        items.add(SettingsItem("Rate Limit Wait Time", "${currentWait}s for ${ProviderRegistry.getById(activeProvider)?.displayName ?: activeProvider}") {
            showRetryWaitDialog(activeProvider, currentWait)
        })

        return items
    }

    // ═══════════════════════════════════════════════════════════
    //  Connectors Dialog
    // ═══════════════════════════════════════════════════════════

    private fun showConnectorsDialog() {
        val connectors = ConnectorManager.getAllConnectors()

        if (connectors.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Connectors")
                .setMessage("No connectors added yet.\nAdd a platform connection (GitHub, GitLab, etc.) to give Leo access to your accounts.")
                .setPositiveButton("Add Connector") { _, _ -> showAddConnectorDialog() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val items = connectors.map { "${it.platformName} (${it.username})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Connectors (${connectors.size})")
            .setItems(items) { _, which ->
                val connector = connectors[which]
                AlertDialog.Builder(this)
                    .setTitle(connector.platformName)
                    .setMessage("Username: ${connector.username}\nEmail: ${connector.email}\nToken: ✓ (hidden)")
                    .setPositiveButton("Edit") { _, _ -> showEditConnectorDialog(connector) }
                    .setNegativeButton("Delete") { _, _ ->
                        ConnectorManager.deleteConnector(connector.id)
                        recreate()
                    }
                    .setNeutralButton("Cancel", null)
                    .show()
            }
            .setPositiveButton("Add Connector") { _, _ -> showAddConnectorDialog() }
            .show()
    }

    private fun showAddConnectorDialog() {
        val layout = LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val etPlatform = EditText(this).apply { hint = "Platform name (e.g. GitHub)" }
        val etUsername = EditText(this).apply { hint = "Username" }
        val etEmail = EditText(this).apply { hint = "Email" }
        val etToken = EditText(this).apply { hint = "Token / API Key" }
        layout.addView(etPlatform)
        layout.addView(etUsername)
        layout.addView(etEmail)
        layout.addView(etToken)

        AlertDialog.Builder(this)
            .setTitle("Add Connector")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val platform = etPlatform.text.toString().trim()
                val username = etUsername.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val token = etToken.text.toString().trim()
                if (platform.isNotBlank() && token.isNotBlank()) {
                    ConnectorManager.addConnector(platform, username, email, token)
                    Toast.makeText(this, "Connector added: $platform", Toast.LENGTH_SHORT).show()
                    recreate()
                } else {
                    Toast.makeText(this, "Platform name and token required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditConnectorDialog(connector: ConnectorManager.Connector) {
        val layout = LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val etPlatform = EditText(this).apply { setText(connector.platformName) }
        val etUsername = EditText(this).apply { setText(connector.username) }
        val etEmail = EditText(this).apply { setText(connector.email) }
        val etToken = EditText(this).apply { setText(connector.token) }
        layout.addView(etPlatform)
        layout.addView(etUsername)
        layout.addView(etEmail)
        layout.addView(etToken)

        AlertDialog.Builder(this)
            .setTitle("Edit ${connector.platformName}")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                ConnectorManager.updateConnector(connector.id,
                    etPlatform.text.toString().trim(),
                    etUsername.text.toString().trim(),
                    etEmail.text.toString().trim(),
                    etToken.text.toString().trim())
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════
    //  Retry Wait Time Dialog
    // ═══════════════════════════════════════════════════════════

    private fun showRetryWaitDialog(providerId: String, currentWait: Int) {
        val options = arrayOf("15 seconds", "30 seconds", "45 seconds", "60 seconds", "90 seconds", "120 seconds")
        val values = intArrayOf(15, 30, 45, 60, 90, 120)
        AlertDialog.Builder(this)
            .setTitle("Rate Limit Wait Time\n(Provider: ${ProviderRegistry.getById(providerId)?.displayName ?: providerId})")
            .setSingleChoiceItems(options, values.indexOf(currentWait).coerceAtLeast(0)) { dialog, which ->
                SecurityManager.setRetryWaitSeconds(providerId, values[which])
                Toast.makeText(this, "Wait time set to ${values[which]}s", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTextEdit(title: String, current: String, onSave: (String) -> Unit) {
        val input = EditText(this).apply { setText(current) }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Save") { _, _ -> onSave(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBehaviorDialog() {
        val behaviors = arrayOf("Professional", "Friendly", "Concise", "Creative", "Custom")
        AlertDialog.Builder(this)
            .setTitle("Select Behavior")
            .setItems(behaviors) { _, which ->
                val behavior = when (which) {
                    0 -> "Be professional and precise"
                    1 -> "Be friendly and warm"
                    2 -> "Be concise and direct"
                    3 -> "Be creative and expressive"
                    else -> {
                        showTextEdit("Custom Behavior", SecurityManager.getBehavior()) { SecurityManager.setBehavior(it) }
                        return@setItems
                    }
                }
                SecurityManager.setBehavior(behavior)
                Toast.makeText(this, "Behavior set", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showMemoriesDialog() {
        val memories = MemoryManager.getAllMemories()
        if (memories.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Memories")
                .setMessage("No memories saved yet.")
                .setPositiveButton("Add Memory") { _, _ ->
                    showTextEdit("New Memory", "") { MemoryManager.addMemory(it) }
                }
                .show()
            return
        }

        val items = memories.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Memories (${memories.size})")
            .setItems(items) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle(memories[which])
                    .setPositiveButton("Delete") { _, _ ->
                        MemoryManager.removeMemory(which)
                        showMemoriesDialog()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setPositiveButton("Add") { _, _ ->
                showTextEdit("New Memory", "") { MemoryManager.addMemory(it) }
            }
            .setNeutralButton("Clear All") { _, _ ->
                MemoryManager.clearAll()
                recreate()
            }
            .show()
    }

    private fun showThemeDialog() {
        val themes = arrayOf("Light", "Dark", "System")
        AlertDialog.Builder(this)
            .setTitle("Theme")
            .setItems(themes) { _, which ->
                val theme = when (which) {
                    0 -> "light"
                    1 -> "dark"
                    else -> "system"
                }
                SecurityManager.setTheme(theme)
                recreate()
            }
            .show()
    }

    private fun showColorDialog(type: String) {
        val colors = arrayOf("#1A1A2E", "#16213E", "#0F3460", "#533483", "#E94560",
            "#10B981", "#F59E0B", "#6366F1", "#EF4444", "#06B6D4")
        AlertDialog.Builder(this)
            .setTitle("Select Color")
            .setItems(colors) { _, which ->
                if (type == "user") SecurityManager.setUserMsgColor(colors[which])
                else SecurityManager.setAgentMsgColor(colors[which])
                recreate()
            }
            .show()
    }

    private fun showLanguageDialog() {
        val langs = arrayOf("English", "Hindi", "Spanish", "French", "German", "Chinese", "Japanese")
        val codes = arrayOf("en", "hi", "es", "fr", "de", "zh", "ja")
        AlertDialog.Builder(this)
            .setTitle("Language")
            .setItems(langs) { _, which ->
                SecurityManager.setAppLanguage(codes[which])
                recreate()
            }
            .show()
    }

    private fun showNotificationsDialog() {
        val items = arrayOf(
            "New messages: ${if (SecurityManager.isNotifMessagesEnabled()) "ON" else "OFF"}",
            "Completed work: ${if (SecurityManager.isNotifWorkEnabled()) "ON" else "OFF"}",
            "Completed tasks: ${if (SecurityManager.isNotifTasksEnabled()) "ON" else "OFF"}",
            "Task reminders: ${if (SecurityManager.isNotifRemindersEnabled()) "ON" else "OFF"}"
        )
        AlertDialog.Builder(this)
            .setTitle("Notifications")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> SecurityManager.setNotifMessages(!SecurityManager.isNotifMessagesEnabled())
                    1 -> SecurityManager.setNotifWork(!SecurityManager.isNotifWorkEnabled())
                    2 -> SecurityManager.setNotifTasks(!SecurityManager.isNotifTasksEnabled())
                    3 -> SecurityManager.setNotifReminders(!SecurityManager.isNotifRemindersEnabled())
                }
                showNotificationsDialog()
            }
            .show()
    }

    private fun showProviderDialog() {
        val providers = ProviderRegistry.ALL
        val names = providers.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Provider (${providers.size} available)")
            .setItems(names) { _, which ->
                val provider = providers[which]
                SecurityManager.setActiveProvider(provider.id)
                Toast.makeText(this, "Provider: ${provider.displayName}", Toast.LENGTH_SHORT).show()
                recreate()
            }
            .show()
    }
}

// ── Settings Adapter ──

data class SettingsItem(val title: String, val subtitle: String, val onClick: () -> Unit)

class SettingsAdapter(private val items: List<SettingsItem>) : RecyclerView.Adapter<SettingsAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(android.R.id.text1)
        val tvSubtitle: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvSubtitle.text = item.subtitle
        holder.itemView.setOnClickListener { item.onClick() }
    }

    override fun getItemCount(): Int = items.size
}
