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
import com.shslab.leo.chat.ChatFile
import java.io.File
import android.content.Intent
import android.net.Uri

class LibraryActivity : AppCompatActivity() {

    private lateinit var chatDb: ChatDatabase
    private lateinit var adapter: FileAdapter
    private lateinit var listView: RecyclerView
    private val selectedFiles = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatDb = ChatDatabase(this)

        val layout = LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Header with actions
        val header = LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply { text = "Library"; textSize = 20f; setPadding(0, 0, 16, 16) }
        val btnDownload = Button(this).apply { text = "Download Selected" }
        val btnDelete = Button(this).apply { text = "Delete Selected" }
        header.addView(title)
        header.addView(btnDownload)
        header.addView(btnDelete)
        layout.addView(header)

        // List
        listView = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@LibraryActivity) }
        adapter = FileAdapter(chatDb.getAllFiles(),
            onSelect = { fileId, selected ->
                if (selected) selectedFiles.add(fileId) else selectedFiles.remove(fileId)
            },
            onClick = { file -> showFileOptions(file) }
        )
        listView.adapter = adapter
        layout.addView(listView)

        btnDownload.setOnClickListener { downloadSelected() }
        btnDelete.setOnClickListener { deleteSelected() }

        setContentView(layout)
        loadFiles()
    }

    private fun loadFiles() {
        val files = chatDb.getAllFiles()
        adapter.updateFiles(files)
        if (files.isEmpty()) {
            Toast.makeText(this, "No files uploaded yet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadSelected() {
        val files = chatDb.getAllFiles().filter { it.id in selectedFiles }
        for (file in files) {
            try {
                val src = Uri.parse(file.filePath)
                val dest = File(getExternalFilesDir(null), file.fileName)
                contentResolver.openInputStream(src)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                Toast.makeText(this, "Downloaded: ${file.fileName}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Download failed: ${file.fileName}", Toast.LENGTH_SHORT).show()
            }
        }
        selectedFiles.clear()
    }

    private fun deleteSelected() {
        val files = chatDb.getAllFiles().filter { it.id in selectedFiles }
        AlertDialog.Builder(this)
            .setTitle("Delete ${files.size} files?")
            .setPositiveButton("Delete") { _, _ ->
                for (file in files) {
                    chatDb.deleteFile(file.id)
                }
                selectedFiles.clear()
                loadFiles()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFileOptions(file: ChatFile) {
        AlertDialog.Builder(this)
            .setTitle(file.fileName)
            .setItems(arrayOf("Download", "Delete")) { _, which ->
                when (which) {
                    0 -> {
                        try {
                            val src = Uri.parse(file.filePath)
                            val dest = File(getExternalFilesDir(null), file.fileName)
                            contentResolver.openInputStream(src)?.use { input ->
                                dest.outputStream().use { output -> input.copyTo(output) }
                            }
                            Toast.makeText(this, "Downloaded: ${file.fileName}", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        chatDb.deleteFile(file.id)
                        loadFiles()
                    }
                }
            }
            .show()
    }
}

class FileAdapter(
    private var files: List<ChatFile>,
    private val onSelect: (String, Boolean) -> Unit,
    private val onClick: (ChatFile) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    fun updateFiles(newFiles: List<ChatFile>) {
        files = newFiles
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(android.R.id.text1)
        val tvType: TextView = view.findViewById(android.R.id.text2)
        val checkbox: CheckBox = view.findViewById(android.R.id.checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_file, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = files[position]
        holder.tvName.text = file.fileName
        holder.tvType.text = "${file.fileType} • ${java.text.SimpleDateFormat("MMM dd").format(java.util.Date(file.timestamp))}"
        holder.checkbox.setOnCheckedChangeListener { _, checked -> onSelect(file.id, checked) }
        holder.itemView.setOnClickListener { onClick(file) }
    }

    override fun getItemCount(): Int = files.size
}
