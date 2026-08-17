package com.shslab.leo.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import org.json.JSONObject
import java.util.UUID

/**
 * LEO SCHEDULED TASKS — Create, manage, execute scheduled AI tasks
 */
class ScheduleManager(private val context: Context) {

    data class ScheduledTask(
        val id: String,
        val title: String,
        val prompt: String,
        val scheduledTime: Long,
        val isRecurring: Boolean = false,
        val recurringInterval: Long = 0,
        val isEnabled: Boolean = true
    )

    fun scheduleTask(title: String, prompt: String, scheduledTime: Long): String {
        val id = UUID.randomUUID().toString()
        val task = ScheduledTask(id, title, prompt, scheduledTime)

        // Save to shared prefs (simple approach)
        saveTask(task)

        // Set alarm
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            putExtra("task_id", id)
            putExtra("title", title)
            putExtra("prompt", prompt)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent)
        }

        return id
    }

    fun cancelTask(taskId: String) {
        val intent = Intent(context, ScheduleReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, taskId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        removeTask(taskId)
    }

    fun getAllTasks(): List<ScheduledTask> {
        val prefs = context.getSharedPreferences("leo_schedule", Context.MODE_PRIVATE)
        val tasks = mutableListOf<ScheduledTask>()
        val all = prefs.all
        for ((key, value) in all) {
            if (key.startsWith("task_")) {
                try {
                    val json = JSONObject(value as String)
                    tasks.add(ScheduledTask(
                        id = json.getString("id"),
                        title = json.getString("title"),
                        prompt = json.getString("prompt"),
                        scheduledTime = json.getLong("scheduledTime"),
                        isRecurring = json.optBoolean("isRecurring", false),
                        recurringInterval = json.optLong("recurringInterval", 0),
                        isEnabled = json.optBoolean("isEnabled", true)
                    ))
                } catch (e: Exception) {}
            }
        }
        return tasks.sortedBy { it.scheduledTime }
    }

    private fun saveTask(task: ScheduledTask) {
        val prefs = context.getSharedPreferences("leo_schedule", Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("id", task.id)
            put("title", task.title)
            put("prompt", task.prompt)
            put("scheduledTime", task.scheduledTime)
            put("isRecurring", task.isRecurring)
            put("recurringInterval", task.recurringInterval)
            put("isEnabled", task.isEnabled)
        }
        prefs.edit().putString("task_${task.id}", json.toString()).apply()
    }

    private fun removeTask(taskId: String) {
        val prefs = context.getSharedPreferences("leo_schedule", Context.MODE_PRIVATE)
        prefs.edit().remove("task_$taskId").apply()
    }
}
