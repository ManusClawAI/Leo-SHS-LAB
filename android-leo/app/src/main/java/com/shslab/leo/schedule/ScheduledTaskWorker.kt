package com.shslab.leo.schedule

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.shslab.leo.NotificationHelper
import com.shslab.leo.network.LeoNetworkClient

class ScheduledTaskWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prompt = inputData.getString("prompt") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Scheduled Task"

        return try {
            val client = LeoNetworkClient()
            val response = client.sendAgentic(
                userMessage = prompt,
                systemPrompt = "You are SHS Leo, an AI assistant. Execute this scheduled task: $title",
                useMemory = true
            )
            NotificationHelper.sendTaskNotification(applicationContext, title, response.take(200))
            Result.success()
        } catch (e: Exception) {
            NotificationHelper.sendTaskNotification(applicationContext, "Task Failed: $title", e.message ?: "Unknown error")
            Result.failure()
        }
    }
}
