package com.shslab.leo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL_ID = "shs_leo_notifications"
    private const val CHANNEL_NAME = "SHS Leo Notifications"

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "SHS Leo assistant notifications"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        initialized = true
    }

    /**
     * Send a notification that opens MainActivity when clicked.
     * @param context Context
     * @param title Notification title
     * @param message Notification message
     * @param sessionId Optional session ID to open
     * @param notificationId Unique ID for this notification
     */
    fun sendNotification(
        context: Context,
        title: String,
        message: String,
        sessionId: String? = null,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        init(context)

        // Check if notifications are enabled
        val notifEnabled = com.shslab.leo.security.SecurityManager.isNotifMessagesEnabled()
        if (!notifEnabled) return

        // Create intent to open MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (sessionId != null) {
                putExtra("session_id", sessionId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted on Android 13+
        }
    }

    /**
     * Send a task notification that opens ScheduleActivity
     */
    fun sendTaskNotification(
        context: Context,
        title: String,
        message: String,
        taskId: String? = null
    ) {
        if (!com.shslab.leo.security.SecurityManager.isNotifTasksEnabled()) return
        init(context)

        val intent = Intent(context, ScheduleActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (taskId != null) putExtra("task_id", taskId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, title.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SHS Leo Task: $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
        } catch (e: SecurityException) {}
    }
}
