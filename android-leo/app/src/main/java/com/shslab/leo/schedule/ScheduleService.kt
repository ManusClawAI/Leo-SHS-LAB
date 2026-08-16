package com.shslab.leo.schedule

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Service that executes scheduled AI tasks
 */
class ScheduleService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prompt = intent?.getStringExtra("prompt") ?: ""
        val title = intent?.getStringExtra("title") ?: "Scheduled Task"

        // Create a WorkManager request to execute the task
        val workRequest = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(workDataOf(
                "prompt" to prompt,
                "title" to title
            ))
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)

        stopSelf()
        return START_NOT_STICKY
    }
}
