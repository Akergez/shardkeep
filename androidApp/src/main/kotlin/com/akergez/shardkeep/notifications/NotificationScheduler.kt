package com.akergez.shardkeep.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    const val CHANNEL_ID = "shardkeep_tasks"
    const val WORK_TAG = "shardkeep_daily_notifications"
    private const val NOTIFY_HOUR = 9

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Задачи Shardkeep",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Ежедневное напоминание о задачах на сегодня"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun scheduleNext(context: Context) {
        val prefs = context.getSharedPreferences("notes_directory", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) return

        val delayMs = millisUntilNextNotifyHour()
        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_TAG,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
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
}
