package com.shslab.leo.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * LEO CHAT DATABASE — Session history, messages, files
 */
class ChatDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "leo_chat.db"
        private const val DB_VERSION = 1

        private const val TABLE_SESSIONS = "sessions"
        private const val TABLE_MESSAGES = "messages"
        private const val TABLE_FILES = "files"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_SESSIONS (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                pinned INTEGER DEFAULT 0,
                archived INTEGER DEFAULT 0,
                is_temporary INTEGER DEFAULT 0
            )
        """)
        db.execSQL("""
            CREATE TABLE $TABLE_MESSAGES (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                liked INTEGER DEFAULT 0,
                disliked INTEGER DEFAULT 0,
                image_path TEXT,
                FOREIGN KEY (session_id) REFERENCES $TABLE_SESSIONS(id)
            )
        """)
        db.execSQL("""
            CREATE TABLE $TABLE_FILES (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                file_path TEXT NOT NULL,
                file_name TEXT NOT NULL,
                file_type TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY (session_id) REFERENCES $TABLE_SESSIONS(id)
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FILES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }

    // ── Sessions ──

    fun createSession(title: String = "New Chat", isTemporary: Boolean = false): String {
        val id = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("id", id)
            put("title", title)
            put("created_at", now)
            put("updated_at", now)
            put("pinned", 0)
            put("archived", 0)
            put("is_temporary", if (isTemporary) 1 else 0)
        }
        writableDatabase.insert(TABLE_SESSIONS, null, cv)
        return id
    }

    fun getSessions(includeArchived: Boolean = false): List<ChatSession> {
        val sessions = mutableListOf<ChatSession>()
        val archCond = if (includeArchived) "" else "AND archived = 0"
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_SESSIONS WHERE is_temporary = 0 $archCond ORDER BY pinned DESC, updated_at DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                sessions.add(ChatSession(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                    updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at")),
                    pinned = it.getInt(it.getColumnIndexOrThrow("pinned")) == 1,
                    archived = it.getInt(it.getColumnIndexOrThrow("archived")) == 1
                ))
            }
        }
        return sessions
    }

    fun getPinnedSessions(): List<ChatSession> {
        val sessions = mutableListOf<ChatSession>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_SESSIONS WHERE pinned = 1 AND archived = 0 ORDER BY updated_at DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                sessions.add(ChatSession(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                    updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at")),
                    pinned = true, archived = false
                ))
            }
        }
        return sessions
    }

    fun getArchivedSessions(): List<ChatSession> {
        val sessions = mutableListOf<ChatSession>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_SESSIONS WHERE archived = 1 ORDER BY updated_at DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                sessions.add(ChatSession(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                    updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at")),
                    pinned = false, archived = true
                ))
            }
        }
        return sessions
    }

    fun renameSession(id: String, title: String) {
        val cv = ContentValues().apply { put("title", title); put("updated_at", System.currentTimeMillis()) }
        writableDatabase.update(TABLE_SESSIONS, cv, "id = ?", arrayOf(id))
    }

    fun pinSession(id: String, pinned: Boolean) {
        val cv = ContentValues().apply { put("pinned", if (pinned) 1 else 0) }
        writableDatabase.update(TABLE_SESSIONS, cv, "id = ?", arrayOf(id))
    }

    fun archiveSession(id: String, archived: Boolean) {
        val cv = ContentValues().apply { put("archived", if (archived) 1 else 0) }
        writableDatabase.update(TABLE_SESSIONS, cv, "id = ?", arrayOf(id))
    }

    fun deleteSession(id: String) {
        writableDatabase.delete(TABLE_MESSAGES, "session_id = ?", arrayOf(id))
        writableDatabase.delete(TABLE_FILES, "session_id = ?", arrayOf(id))
        writableDatabase.delete(TABLE_SESSIONS, "id = ?", arrayOf(id))
    }

    fun searchSessions(query: String): List<ChatSession> {
        val sessions = mutableListOf<ChatSession>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_SESSIONS WHERE title LIKE '%$query%' AND archived = 0 ORDER BY updated_at DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                sessions.add(ChatSession(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                    updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at")),
                    pinned = it.getInt(it.getColumnIndexOrThrow("pinned")) == 1,
                    archived = false
                ))
            }
        }
        return sessions
    }

    // ── Messages ──

    fun addMessage(sessionId: String, role: String, content: String, imagePath: String? = null): String {
        val id = java.util.UUID.randomUUID().toString()
        val cv = ContentValues().apply {
            put("id", id)
            put("session_id", sessionId)
            put("role", role)
            put("content", content)
            put("timestamp", System.currentTimeMillis())
            put("image_path", imagePath)
        }
        writableDatabase.insert(TABLE_MESSAGES, null, cv)
        // Update session timestamp
        val scv = ContentValues().apply { put("updated_at", System.currentTimeMillis()) }
        writableDatabase.update(TABLE_SESSIONS, scv, "id = ?", arrayOf(sessionId))
        return id
    }

    fun getMessages(sessionId: String): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_MESSAGES WHERE session_id = ? ORDER BY timestamp ASC", arrayOf(sessionId))
        cursor.use {
            while (it.moveToNext()) {
                messages.add(ChatMessage(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    sessionId = sessionId,
                    role = it.getString(it.getColumnIndexOrThrow("role")),
                    content = it.getString(it.getColumnIndexOrThrow("content")),
                    timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                    liked = it.getInt(it.getColumnIndexOrThrow("liked")) == 1,
                    disliked = it.getInt(it.getColumnIndexOrThrow("disliked")) == 1,
                    imagePath = it.getString(it.getColumnIndexOrThrow("image_path"))
                ))
            }
        }
        return messages
    }

    fun updateMessage(id: String, content: String) {
        val cv = ContentValues().apply { put("content", content) }
        writableDatabase.update(TABLE_MESSAGES, cv, "id = ?", arrayOf(id))
    }

    fun deleteMessage(id: String) {
        writableDatabase.delete(TABLE_MESSAGES, "id = ?", arrayOf(id))
    }

    fun setLiked(id: String, liked: Boolean) {
        val cv = ContentValues().apply {
            put("liked", if (liked) 1 else 0)
            if (liked) put("disliked", 0)
        }
        writableDatabase.update(TABLE_MESSAGES, cv, "id = ?", arrayOf(id))
    }

    fun setDisliked(id: String, disliked: Boolean) {
        val cv = ContentValues().apply {
            put("disliked", if (disliked) 1 else 0)
            if (disliked) put("liked", 0)
        }
        writableDatabase.update(TABLE_MESSAGES, cv, "id = ?", arrayOf(id))
    }

    // ── Files ──

    fun addFile(sessionId: String, filePath: String, fileName: String, fileType: String): String {
        val id = java.util.UUID.randomUUID().toString()
        val cv = ContentValues().apply {
            put("id", id)
            put("session_id", sessionId)
            put("file_path", filePath)
            put("file_name", fileName)
            put("file_type", fileType)
            put("timestamp", System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE_FILES, null, cv)
        return id
    }

    fun getFiles(sessionId: String): List<ChatFile> {
        val files = mutableListOf<ChatFile>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_FILES WHERE session_id = ? ORDER BY timestamp DESC", arrayOf(sessionId))
        cursor.use {
            while (it.moveToNext()) {
                files.add(ChatFile(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    sessionId = sessionId,
                    filePath = it.getString(it.getColumnIndexOrThrow("file_path")),
                    fileName = it.getString(it.getColumnIndexOrThrow("file_name")),
                    fileType = it.getString(it.getColumnIndexOrThrow("file_type")),
                    timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                ))
            }
        }
        return files
    }

    fun deleteFile(id: String) {
        writableDatabase.delete(TABLE_FILES, "id = ?", arrayOf(id))
    }

    // ── Library (all files) ──

    fun getAllFiles(): List<ChatFile> {
        val files = mutableListOf<ChatFile>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_FILES ORDER BY timestamp DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                files.add(ChatFile(
                    id = it.getString(it.getColumnIndexOrThrow("id")),
                    sessionId = it.getString(it.getColumnIndexOrThrow("session_id")),
                    filePath = it.getString(it.getColumnIndexOrThrow("file_path")),
                    fileName = it.getString(it.getColumnIndexOrThrow("file_name")),
                    fileType = it.getString(it.getColumnIndexOrThrow("file_type")),
                    timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                ))
            }
        }
        return files
    }
}

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
    val archived: Boolean
)

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: String,       // "user" or "assistant"
    val content: String,
    val timestamp: Long,
    val liked: Boolean = false,
    val disliked: Boolean = false,
    val imagePath: String? = null
)

data class ChatFile(
    val id: String,
    val sessionId: String,
    val filePath: String,
    val fileName: String,
    val fileType: String,
    val timestamp: Long
)
