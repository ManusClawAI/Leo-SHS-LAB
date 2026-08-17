package com.shslab.leo.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver for scheduled task alarms
 */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: return
        val title = intent.getStringExtra("title") ?: "Scheduled Task"
        val prompt = intent.getStringExtra("prompt") ?: ""

        // Start the ScheduleService to execute the task
        val serviceIntent = Intent(context, ScheduleService::class.java).apply {
            putExtra("task_id", taskId)
            putExtra("title", title)
            putExtra("prompt", prompt)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
