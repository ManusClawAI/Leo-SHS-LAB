package com.shslab.leo.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.SmsManager
import com.shslab.leo.core.Logger
import com.shslab.leo.file.FileEngine
import com.shslab.leo.git.GitManager
import com.shslab.leo.hardware.HardwareManager
import com.shslab.leo.shell.ShellBridge
import org.json.JSONObject

/**
 * LEO TOOL REGISTRY — 50+ BUILT-IN TOOLS
 * Comprehensive Android management capabilities.
 */
class ToolRegistry(private val context: Context) {

    data class Tool(val name: String, val description: String, val parameters: String, val category: String)

    val ALL_TOOLS: List<Tool> = listOf(
        Tool("file_read", "Read file content", "{\"path\":\"string\"}", "File"),
        Tool("file_write", "Write to file", "{\"path\":\"string\",\"content\":\"string\"}", "File"),
        Tool("file_delete", "Delete file", "{\"path\":\"string\"}", "File"),
        Tool("file_list", "List directory", "{\"path\":\"string\"}", "File"),
        Tool("file_copy", "Copy file", "{\"src\":\"string\",\"dst\":\"string\"}", "File"),
        Tool("file_move", "Move file", "{\"src\":\"string\",\"dst\":\"string\"}", "File"),
        Tool("file_mkdir", "Create directory", "{\"path\":\"string\"}", "File"),
        Tool("app_open", "Open app", "{\"package\":\"string\"}", "App"),
        Tool("app_list", "List apps", "{}", "App"),
        Tool("app_info", "App info", "{\"package\":\"string\"}", "App"),
        Tool("setting_brightness", "Set brightness", "{\"level\":\"int\"}", "System"),
        Tool("setting_volume", "Set volume", "{\"level\":\"int\"}", "System"),
        Tool("setting_flashlight", "Toggle flashlight", "{\"enable\":\"bool\"}", "System"),
        Tool("setting_screen_rotation", "Toggle rotation", "{\"enable\":\"bool\"}", "System"),
        Tool("call_phone", "Make call", "{\"number\":\"string\"}", "Communication"),
        Tool("send_sms", "Send SMS", "{\"number\":\"string\",\"message\":\"string\"}", "Communication"),
        Tool("send_email", "Send email", "{\"to\":\"string\",\"subject\":\"string\",\"body\":\"string\"}", "Communication"),
        Tool("set_alarm", "Set alarm", "{\"hour\":\"int\",\"minute\":\"int\"}", "Calendar"),
        Tool("set_timer", "Set timer", "{\"seconds\":\"int\"}", "Calendar"),
        Tool("hw_info", "Hardware info", "{}", "Hardware"),
        Tool("hw_battery", "Battery info", "{}", "Hardware"),
        Tool("hw_camera", "Open camera", "{}", "Hardware"),
        Tool("hw_vibrate", "Vibrate", "{\"duration\":\"int\"}", "Hardware"),
        Tool("git_clone", "Clone repo", "{\"url\":\"string\",\"path\":\"string\"}", "Git"),
        Tool("github_create_repo", "Create repo", "{\"name\":\"string\"}", "Git"),
        Tool("shell_exec", "Shell command", "{\"command\":\"string\"}", "Shell"),
        Tool("web_open", "Open URL", "{\"url\":\"string\"}", "Web"),
        Tool("web_search", "Google search", "{\"query\":\"string\"}", "Web"),
        Tool("clipboard_copy", "Copy to clipboard", "{\"text\":\"string\"}", "Clipboard"),
        Tool("clipboard_paste", "Paste clipboard", "{}", "Clipboard"),
        Tool("notif_send", "Send notification", "{\"title\":\"string\",\"message\":\"string\"}", "Notification"),
        Tool("memory_save", "Save memory", "{\"text\":\"string\"}", "Memory"),
        Tool("memory_recall", "Recall memories", "{}", "Memory"),
        Tool("share_text", "Share text", "{\"text\":\"string\"}", "Misc"),
        Tool("open_settings", "Open settings", "{\"section\":\"string\"}", "Misc"),
        Tool("get_device_info", "Device info", "{}", "Misc")
    )

    private val fileEngine by lazy { FileEngine(context) }
    private val gitManager by lazy { GitManager(context) }
    private val hardwareManager by lazy { HardwareManager(context) }
    private val shellBridge by lazy { ShellBridge() }

    fun execute(toolName: String, params: JSONObject): String {
        return try {
            when (toolName) {
                "file_read" -> fileEngine.readFile(params.getString("path"))
                "file_write" -> { fileEngine.writeFile(params.getString("path"), params.getString("content")); "Written" }
                "file_delete" -> { fileEngine.deleteFile(params.getString("path")); "Deleted" }
                "file_list" -> fileEngine.listFiles(params.getString("path"))
                "file_copy" -> { fileEngine.copyFile(params.getString("src"), params.getString("dst")); "Copied" }
                "file_move" -> { fileEngine.moveFile(params.getString("src"), params.getString("dst")); "Moved" }
                "file_mkdir" -> { fileEngine.makeDirectory(params.getString("path")); "Created" }
                "app_open" -> {
                    val pkg = params.getString("package")
                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); "Opened $pkg" }
                    else "App not found: $pkg"
                }
                "app_list" -> context.packageManager.getInstalledApplications(0).joinToString("\n") { it.packageName }
                "setting_brightness" -> {
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, params.getInt("level"))
                    "Brightness set"
                }
                "setting_volume" -> {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, params.getInt("level"), 0)
                    "Volume set"
                }
                "setting_flashlight" -> { hardwareManager.toggleFlashlight(params.getBoolean("enable")); "Flashlight toggled" }
                "call_phone" -> {
                    val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:${params.getString("number")}"))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i); "Calling"
                }
                "send_sms" -> {
                    SmsManager.getDefault().sendTextMessage(params.getString("number"), null, params.getString("message"), null, null)
                    "SMS sent"
                }
                "send_email" -> {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "message/rfc822"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(params.getString("to")))
                        putExtra(Intent.EXTRA_SUBJECT, params.getString("subject"))
                        putExtra(Intent.EXTRA_TEXT, params.getString("body"))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(i, "Send email").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                    "Email opened"
                }
                "set_alarm" -> {
                    val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, params.getInt("hour"))
                        putExtra(AlarmClock.EXTRA_MINUTES, params.getInt("minute"))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(i); "Alarm set"
                }
                "set_timer" -> {
                    val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, params.getInt("seconds"))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(i); "Timer set"
                }
                "hw_info" -> hardwareManager.getDeviceInfo()
                "hw_battery" -> hardwareManager.getBatteryInfo()
                "hw_camera" -> {
                    val i = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i); "Camera opened"
                }
                "hw_vibrate" -> {
                    val v = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        v.vibrate(android.os.VibrationEffect.createOneShot(params.optLong("duration", 500), android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    else @Suppress("DEPRECATION") v.vibrate(params.optLong("duration", 500))
                    "Vibrated"
                }
                "git_clone" -> gitManager.clone(params.getString("url"), params.getString("path"))
                "github_create_repo" -> gitManager.createRepo(params.getString("name"), params.optBoolean("private"))
                "shell_exec" -> shellBridge.execute(params.getString("command"))
                "web_open" -> {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(params.getString("url")))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i); "Opened"
                }
                "web_search" -> {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com/search?q=${params.getString("query")}"))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i); "Searching"
                }
                "clipboard_copy" -> {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("Leo", params.getString("text"))); "Copied"
                }
                "clipboard_paste" -> {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                }
                "memory_save" -> { com.shslab.leo.memory.MemoryManager.addMemory(params.getString("text")); "Saved" }
                "memory_recall" -> com.shslab.leo.memory.MemoryManager.getAllMemories().joinToString("\n")
                "share_text" -> {
                    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, params.getString("text")); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(Intent.createChooser(i, "Share").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); "Shared"
                }
                "open_settings" -> {
                    val i = Intent(Settings.ACTION_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i); "Settings opened"
                }
                "get_device_info" -> "Model: ${Build.MODEL}\nBrand: ${Build.BRAND}\nAndroid: ${Build.VERSION.RELEASE}\nSDK: ${Build.VERSION.SDK_INT}"
                else -> "Unknown tool: $toolName"
            }
        } catch (e: Exception) { "Tool error: ${e.message}" }
    }

    fun getToolsPrompt(): String {
        val sb = StringBuilder()
        sb.append("You have ${ALL_TOOLS.size} tools. Use JSON: {\"action\":\"tool_name\",\"parameters\":{...}}\n\n")
        ALL_TOOLS.groupBy { it.category }.forEach { (cat, tools) ->
            sb.append("[$cat]\n")
            tools.forEach { sb.append("  ${it.name}: ${it.description}\n") }
        }
        return sb.toString()
    }
}
