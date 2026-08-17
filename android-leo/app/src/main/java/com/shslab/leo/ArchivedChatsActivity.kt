package com.shslab.leo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shslab.leo.chat.ChatDatabase
import com.shslab.leo.chat.ChatSession
import com.shslab.leo.chat.SessionAdapter

class ArchivedChatsActivity : AppCompatActivity() {

    private lateinit var chatDb: ChatDatabase
    private lateinit var adapter: SessionAdapter
    private lateinit var listView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatDb = ChatDatabase(this)

        val layout = LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Header
        val header = TextView(this).apply {
            text = "Archived Chats"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(header)

        // List
        listView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ArchivedChatsActivity)
        }
        adapter = SessionAdapter(
            onClick = { session -> restoreSession(session) },
            onLongClick = { session -> showOptions(session) }
        )
        listView.adapter = adapter
        layout.addView(listView)

        // Empty state
        val emptyText = TextView(this).apply {
            text = "No archived chats."
            textSize = 16f
            setPadding(0, 32, 0, 0)
            visibility = View.GONE
        }
        layout.addView(emptyText)

        setContentView(layout)
        loadArchivedChats()
    }

    private fun loadArchivedChats() {
        val archived = chatDb.getArchivedSessions()
        adapter.setSessions(archived)
        if (archived.isEmpty()) {
            Toast.makeText(this, "No archived chats", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreSession(session: ChatSession) {
        AlertDialog.Builder(this)
            .setTitle("Restore chat")
            .setMessage("Restore '${session.title}'?")
            .setPositiveButton("Restore") { _, _ ->
                chatDb.archiveSession(session.id, false)
                Toast.makeText(this, "Chat restored", Toast.LENGTH_SHORT).show()
                loadArchivedChats()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOptions(session: ChatSession) {
        AlertDialog.Builder(this)
            .setTitle(session.title)
            .setItems(arrayOf("Restore", "Delete")) { _, which ->
                when (which) {
                    0 -> restoreSession(session)
                    1 -> {
                        AlertDialog.Builder(this)
                            .setTitle("Delete chat?")
                            .setMessage("This cannot be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                chatDb.deleteSession(session.id)
                                loadArchivedChats()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }
}
