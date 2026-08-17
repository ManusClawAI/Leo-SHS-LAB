package com.shslab.leo.tools

/**
 * Helper to get tools prompt without circular dependency.
 * ToolRegistry is instantiated with Context, but this provides a static prompt.
 */
object ToolRegistryPrompt {
    fun getPrompt(): String {
        val sb = StringBuilder()
        sb.append("You have access to the following tools. Use JSON format: {\"action\":\"tool_name\",\"parameters\":{...}}\n\n")
        sb.append("File: file_read, file_write, file_delete, file_list, file_copy, file_move, file_mkdir\n")
        sb.append("App: app_open, app_list, app_info\n")
        sb.append("System: setting_brightness, setting_volume, setting_flashlight, setting_screen_rotation\n")
        sb.append("Communication: call_phone, send_sms, send_email\n")
        sb.append("Calendar: set_alarm, set_timer\n")
        sb.append("Hardware: hw_info, hw_battery, hw_camera, hw_vibrate\n")
        sb.append("Git: git_clone, github_create_repo\n")
        sb.append("Shell: shell_exec\n")
        sb.append("Web: web_open, web_search\n")
        sb.append("Clipboard: clipboard_copy, clipboard_paste\n")
        sb.append("Notification: notif_send\n")
        sb.append("Memory: memory_save, memory_recall\n")
        sb.append("Misc: share_text, open_settings, get_device_info\n")
        return sb.toString()
    }
}
