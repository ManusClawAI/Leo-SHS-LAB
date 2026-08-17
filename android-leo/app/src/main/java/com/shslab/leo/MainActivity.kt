package com.shslab.leo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shslab.leo.chat.*
import com.shslab.leo.core.Logger
import com.shslab.leo.memory.MemoryManager
import com.shslab.leo.network.LeoNetworkClient
import com.shslab.leo.security.SecurityManager
import com.shslab.leo.tools.ToolRegistry
import com.shslab.leo.voice.LeoTtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ═══════════════════════════════════════════════════════════════
 *  LEO MAIN ACTIVITY v7 — MODERN AI ASSISTANT UI
 *
 *  Features:
 *  - ChatGPT/Gemini-style minimal UI
 *  - 3-dot menu (compresses all features)
 *  - Slide-in drawer (half screen)
 *  - Keyboard-aware input
 *  - File/image upload
 *  - Message actions (edit, copy, regenerate, like/dislike, listen)
 *  - Chat history with pin/archive/delete
 *  - TTS voice output
 *  ──────────────────────────────────────────────────────────────
 * ═══════════════════════════════════════════════════════════════
 */
class MainActivity : AppCompatActivity() {

    // ── Views ──
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rvChat: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnMic: ImageButton
    private lateinit var btnAttach: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnNewChat: ImageButton
    private lateinit var btnMore: ImageButton
    private lateinit var btnDrawerNewChat: ImageButton
    private lateinit var btnProfile: ImageButton
    private lateinit var tvTitle: TextView

    // Drawer views
    private lateinit var rvChatSessions: RecyclerView
    private lateinit var rvPinnedChats: RecyclerView

    // ── Components ──
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var sessionAdapter: SessionAdapter
    private lateinit var pinnedAdapter: SessionAdapter
    private lateinit var chatDb: ChatDatabase
    private lateinit var networkClient: LeoNetworkClient
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var ttsManager: LeoTtsManager

    // ── State ──
    private var currentSessionId: String? = null
    private var isVoiceMode = false

    // File picker
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleFileUpload(it) }
    }

    companion object {
        private const val REQUEST_MIC_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)

        // Setup window
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Setup DrawerLayout as root
        drawerLayout = DrawerLayout(this).apply {
            layoutParams = DrawerLayout.LayoutParams(
                DrawerLayout.LayoutParams.MATCH_PARENT,
                DrawerLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Inflate main content and drawer
        layoutInflater.inflate(R.layout.activity_main, drawerLayout, true)
        layoutInflater.inflate(R.layout.drawer_menu, drawerLayout, true)

        setContentView(drawerLayout)

        initViews()
        initComponents()
        setupListeners()
        setupKeyboardAware()
        loadSessions()

        // Start with new chat
        // Check if opened from notification
        val sessionId = intent.getStringExtra("session_id")
        if (sessionId != null) {
            openSession(sessionId)
        } else {
            startNewChat(temporary = true)
        }
    }

    private fun applyTheme() {
        val theme = SecurityManager.getTheme()
        when (theme) {
            "light" -> setTheme(R.style.Theme_Leo_Light)
            else -> setTheme(R.style.Theme_Leo)
        }
    }

    private fun initViews() {
        rvChat = findViewById(R.id.rvChat)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        btnMic = findViewById(R.id.btnMic)
        btnAttach = findViewById(R.id.btnAttach)
        btnMenu = findViewById(R.id.btnMenu)
        btnNewChat = findViewById(R.id.btnNewChat)
        btnMore = findViewById(R.id.btnMore)
        tvTitle = findViewById(R.id.tvTitle)
        btnDrawerNewChat = findViewById(R.id.btnDrawerNewChat)
        btnProfile = findViewById(R.id.btnProfile)
        rvChatSessions = findViewById(R.id.rvChatSessions)
        rvPinnedChats = findViewById(R.id.rvPinnedChats)
    }

    private fun initComponents() {
        chatDb = ChatDatabase(this)
        networkClient = LeoNetworkClient()
        toolRegistry = ToolRegistry(this)
        ttsManager = LeoTtsManager(this)
        ttsManager.init()

        chatAdapter = ChatAdapter(
            context = this,
            onEdit = { msgId, content -> editMessage(msgId, content) },
            onRegenerate = { msgId -> regenerateMessage(msgId) },
            onListen = { content -> listenToMessage(content) },
            onLike = { msgId, liked -> chatDb.setLiked(msgId, liked) },
            onDislike = { msgId, disliked -> chatDb.setDisliked(msgId, disliked) }
        )

        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChat.adapter = chatAdapter

        sessionAdapter = SessionAdapter(
            onClick = { session -> openSession(session.id) },
            onLongClick = { session -> showSessionOptions(session) }
        )
        rvChatSessions.layoutManager = LinearLayoutManager(this)
        rvChatSessions.adapter = sessionAdapter

        pinnedAdapter = SessionAdapter(
            onClick = { session -> openSession(session.id) },
            onLongClick = { session -> showSessionOptions(session) }
        )
        rvPinnedChats.layoutManager = LinearLayoutManager(this)
        rvPinnedChats.adapter = pinnedAdapter

        // Set title to agent name
        tvTitle.text = "SHS Leo"
    }

    private fun setupListeners() {
        // Menu button - open drawer
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // New chat (top bar)
        btnNewChat.setOnClickListener {
            // Check if opened from notification
        val sessionId = intent.getStringExtra("session_id")
        if (sessionId != null) {
            openSession(sessionId)
        } else {
            startNewChat(temporary = true)
        }
        }

        // New chat (drawer)
        btnDrawerNewChat.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            // Check if opened from notification
        val sessionId = intent.getStringExtra("session_id")
        if (sessionId != null) {
            openSession(sessionId)
        } else {
            startNewChat(temporary = true)
        }
        }

        // 3-dot menu
        btnMore.setOnClickListener { view ->
            showMoreMenu(view)
        }

        // Profile
        btnProfile.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Send button
        btnSend.setOnClickListener {
            sendMessage()
        }

        // Mic button
        btnMic.setOnClickListener {
            if (isVoiceMode) {
                ttsManager.stop()
                isVoiceMode = false
                btnMic.setImageResource(R.drawable.ic_mic)
            } else {
                startVoiceInput()
            }
        }

        // Attach button
        btnAttach.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        // Input text change
        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnSend.visibility = if (s.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        })

        // Enter to send
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        // Library button
        findViewById<android.widget.LinearLayout>(R.id.btnLibrary)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            // Open library activity
            startActivity(Intent(this, LibraryActivity::class.java))
        }

        // Scheduled tasks button
        findViewById<android.widget.LinearLayout>(R.id.btnScheduledTasks)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, ScheduleActivity::class.java))
        }

        // Drawer search
        findViewById<ImageButton>(R.id.btnDrawerSearch)?.setOnClickListener {
            showSearchDialog()
        }
    }

    /**
     * Setup keyboard-aware input — input box rises above keyboard
     */
    private fun setupKeyboardAware() {
        // SOFT_INPUT_ADJUST_RESIZE is already set
        // This makes the input container move up when keyboard appears
        val rootView = window.decorView.rootView
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val heightDiff = rootView.rootView.height - rootView.height
            if (heightDiff > rootView.rootView.height * 0.15) {
                // Keyboard is visible
                rvChat.post {
                    if (chatAdapter.itemCount > 0) {
                        rvChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
                    }
                }
            }
        }
    }

    /**
     * 3-dot menu — compresses Overlay, Vault, Settings, Watch, HUD, Accessibility
     */
    private fun showMoreMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_more, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.menu_vault -> {
                    startActivity(Intent(this, VaultActivity::class.java))
                    true
                }
                R.id.menu_surveillance -> {
                    startActivity(Intent(this, SurveillanceActivity::class.java))
                    true
                }
                R.id.menu_overlay -> {
                    toggleOverlay()
                    true
                }
                R.id.menu_accessibility -> {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    true
                }
                R.id.menu_voice -> {
                    val enabled = !SecurityManager.isTTSEnabled()
                    SecurityManager.setTTSEnabled(enabled)
                    Toast.makeText(this, if (enabled) "Voice output ON" else "Voice output OFF", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun toggleOverlay() {
        try {
            val intent = Intent(this, com.shslab.leo.overlay.OverlayService::class.java)
            if (Settings.canDrawOverlays(this)) {
                startService(intent)
            } else {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Overlay error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Send message to AI
     */
    private fun sendMessage() {
        val text = etInput.text.toString().trim()
        val hasAttachment = pendingAttachmentUri != null
        if (text.isBlank() && !hasAttachment) return

        // Build message content — include attachment info
        val messageContent = if (hasAttachment) {
            val fileName = pendingAttachmentName ?: "file"
            if (text.isNotBlank()) {
                "$text\n\n[Attached file: $fileName]"
            } else {
                "[Attached file: $fileName]"
            }
        } else {
            text
        }

        // Auto-save memory if "I like", "remember" etc.
        MemoryManager.autoExtractFromMessage(messageContent)

        // Create session if temporary
        if (currentSessionId == null) {
            currentSessionId = chatDb.createSession(text.take(30), isTemporary = false)
        }

        // Add user message
        val userMsg = ChatMessage(
            id = chatDb.addMessage(currentSessionId!!, "user", text),
            sessionId = currentSessionId!!,
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis()
        )
        chatAdapter.addMessage(userMsg)

        // Clear input and attachment
        etInput.text.clear()
        etInput.hint = "Message SHS Leo..."
        if (pendingAttachmentUri != null) {
            currentSessionId?.let { sid ->
                chatDb.addFile(sid, pendingAttachmentUri.toString(), pendingAttachmentName ?: "file", "file")
            }
        }
        pendingAttachmentUri = null
        pendingAttachmentName = null

        // Update session title if first message
        val messages = chatDb.getMessages(currentSessionId!!)
        if (messages.size == 1) {
            chatDb.renameSession(currentSessionId!!, text.take(30))
            loadSessions()
        }

        // Send to AI
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                try {
                    val systemPrompt = buildSystemPrompt()
                    networkClient.sendAgentic(messageContent, systemPrompt, SecurityManager.isMemoryEnabled())
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }

            // Add assistant message
            val assistantMsg = ChatMessage(
                id = chatDb.addMessage(currentSessionId!!, "assistant", response),
                sessionId = currentSessionId!!,
                role = "assistant",
                content = response,
                timestamp = System.currentTimeMillis()
            )
            chatAdapter.addMessage(assistantMsg)

            // TTS if enabled
            if (SecurityManager.isTTSEnabled()) {
                ttsManager.speak(response)
            }
        }
    }

    private fun buildSystemPrompt(): String {
        val sb = StringBuilder()
        sb.append("You are SHS Leo, an advanced AI assistant.\n")
        sb.append("The user's name is ${SecurityManager.getUserName().ifEmpty { "User" }}.\n\n")

        if (SecurityManager.isToolsEnabled()) {
            sb.append(toolRegistry.getToolsPrompt())
            sb.append("\n")
        }

        val personalization = SecurityManager.getPersonalization()
        if (personalization.isNotBlank()) {
            sb.append("Personalization: $personalization\n")
        }

        val behavior = SecurityManager.getBehavior()
        if (behavior.isNotBlank()) {
            sb.append("Behavior: $behavior\n")
        }

        return sb.toString()
    }

    private fun startNewChat(temporary: Boolean) {
        currentSessionId = if (temporary) null else chatDb.createSession()
        chatAdapter.setMessages(emptyList())
        tvTitle.text = "SHS Leo"
    }

    private fun openSession(sessionId: String) {
        drawerLayout.closeDrawer(GravityCompat.START)
        currentSessionId = sessionId
        val messages = chatDb.getMessages(sessionId)
        chatAdapter.setMessages(messages)
        if (messages.isNotEmpty()) {
            rvChat.scrollToPosition(messages.size - 1)
        }
        val session = chatDb.getSessions().find { it.id == sessionId }
        tvTitle.text = session?.title ?: SecurityManager.getAgentName()
    }

    private fun loadSessions() {
        val sessions = chatDb.getSessions()
        val pinned = chatDb.getPinnedSessions()
        val normal = sessions.filter { !it.pinned }
        sessionAdapter.setSessions(normal)
        pinnedAdapter.setSessions(pinned)
        findViewById<TextView>(R.id.tvPinnedLabel)?.visibility =
            if (pinned.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSessionOptions(session: ChatSession) {
        val options = arrayOf("Rename", "Pin/Unpin", "Archive", "Delete")
        AlertDialog.Builder(this)
            .setTitle(session.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(session)
                    1 -> { chatDb.pinSession(session.id, !session.pinned); loadSessions() }
                    2 -> { chatDb.archiveSession(session.id, true); loadSessions() }
                    3 -> {
                        AlertDialog.Builder(this)
                            .setTitle("Delete chat?")
                            .setMessage("This cannot be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                chatDb.deleteSession(session.id)
                                loadSessions()
                                if (currentSessionId == session.id) startNewChat(true)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showRenameDialog(session: ChatSession) {
        val input = EditText(this).apply { setText(session.title) }
        AlertDialog.Builder(this)
            .setTitle("Rename chat")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                chatDb.renameSession(session.id, input.text.toString())
                loadSessions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply { hint = "Search chats..." }
        AlertDialog.Builder(this)
            .setTitle("Search")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotBlank()) {
                    val results = chatDb.searchSessions(query)
                    sessionAdapter.setSessions(results)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editMessage(msgId: String, content: String) {
        val input = EditText(this).apply { setText(content) }
        AlertDialog.Builder(this)
            .setTitle("Edit message")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newContent = input.text.toString()
                chatDb.updateMessage(msgId, newContent)
                // Reload messages
                currentSessionId?.let { openSession(it) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun regenerateMessage(msgId: String) {
        // Find the user message before this assistant message
        val messages = chatDb.getMessages(currentSessionId ?: return)
        val msgIndex = messages.indexOfFirst { it.id == msgId }
        if (msgIndex > 0) {
            val userMsg = messages[msgIndex - 1]
            // Delete the old assistant message
            chatDb.deleteMessage(msgId)
            // Resend
            lifecycleScope.launch {
                val response = withContext(Dispatchers.IO) {
                    try {
                        networkClient.sendAgentic(userMsg.content, buildSystemPrompt(),
                            SecurityManager.isMemoryEnabled())
                    } catch (e: Exception) { "Error: ${e.message}" }
                }
                chatDb.addMessage(currentSessionId!!, "assistant", response)
                openSession(currentSessionId!!)
            }
        }
    }

    private fun listenToMessage(content: String) {
        ttsManager.speak(content)
        Toast.makeText(this, "Speaking...", Toast.LENGTH_SHORT).show()
    }

    private fun startVoiceInput() {
        if (!SecurityManager.isVoiceEnabled()) {
            Toast.makeText(this, "Voice input disabled in settings", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC_PERMISSION)
            return
        }

        // Use Android's built-in speech recognizer
        try {
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to Leo...")
            }
            voiceInputLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input not available: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val voiceInputLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(
                android.speech.RecognizerIntent.EXTRA_RESULTS
            )?.firstOrNull()
            if (!text.isNullOrBlank()) {
                etInput.setText(text)
                etInput.setSelection(text.length)
            }
        }
    }

    private var pendingAttachmentUri: Uri? = null
    private var pendingAttachmentName: String? = null

    private fun handleFileUpload(uri: Uri) {
        val fileName = getFileName(uri) ?: "uploaded_file"
        pendingAttachmentUri = uri
        pendingAttachmentName = fileName
        // Show the attachment name in the input field
        etInput.hint = "Attached: $fileName (type message or send directly)"
        Toast.makeText(this, "Attached: $fileName", Toast.LENGTH_SHORT).show()
    }

    private fun getFileName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    private fun contentType(uri: Uri): String {
        return contentResolver.getType(uri) ?: "application/octet-stream"
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}
