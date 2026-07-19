package com.akergez.shardkeep.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.akergez.shardkeep.WorkspaceRegistry
import com.akergez.shardkeep.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    const val CHANNEL_ID = "shardkeep_tasks"
    private const val WORK_TAG = "shardkeep_daily_notifications"
    const val KEY_WORKSPACE_ID = "workspace_id"
    private const val NOTIFY_HOUR = 9

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_tasks),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_tasks_description)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun scheduleNext(context: Context, workspaceId: String) {
        val prefs = WorkspaceRegistry(context).preferences(workspaceId)
        if (!prefs.getBoolean("notifications_enabled", true)) return

        val delayMs = millisUntilNextNotifyHour()
        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_WORKSPACE_ID to workspaceId))
            .addTag(workTag(workspaceId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workTag(workspaceId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context, workspaceId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(workTag(workspaceId))
    }

    private fun millisUntilNextNotifyHour(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, NOTIFY_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    private fun workTag(id: String) = "$WORK_TAG-$id"
}
