package com.shslab.leo.schedule

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.shslab.leo.network.LeoNetworkClient

/**
 * Worker that executes scheduled AI tasks in background
 */
class ScheduledTaskWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val prompt = inputData.getString("prompt") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Scheduled Task"

        return try {
            val client = LeoNetworkClient()
            val response = client.sendAgentic(
                userMessage = prompt,
                systemPrompt = "You are Leo, an AI assistant. Execute this scheduled task: $title",
                useMemory = true
            )

            // Send notification with result
            sendNotification(applicationContext, title, response.take(200))
            Result.success()
        } catch (e: Exception) {
            sendNotification(applicationContext, "Task Failed: $title", e.message ?: "Unknown error")
            Result.failure()
        }
    }

    private fun sendNotification(context: Context, title: String, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "leo_scheduled"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(channelId, "Scheduled Tasks",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT)
                notificationManager.createNotificationChannel(channel)
            }
            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .build()
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {}
    }
}
