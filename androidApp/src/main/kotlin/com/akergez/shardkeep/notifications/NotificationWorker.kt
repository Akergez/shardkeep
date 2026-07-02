package com.akergez.shardkeep.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.akergez.shardkeep.MainActivity
import com.akergez.shardkeep.R
import com.akergez.shardkeep.db.DatabaseDriverFactory
import com.akergez.shardkeep.db.createDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("notes_directory", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) {
            NotificationScheduler.scheduleNext(applicationContext)
            return Result.success()
        }
        if (prefs.getString("tree_uri", null) == null) {
            NotificationScheduler.scheduleNext(applicationContext)
            return Result.success()
        }

        val db = runCatching { createDatabase(DatabaseDriverFactory(applicationContext)) }
            .getOrNull() ?: return Result.retry()

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val dueTasks = db.indexedTaskQueries.selectAll().executeAsList().filter { row ->
            row.due_date == today && row.status != "Done"
        }

        if (dueTasks.isNotEmpty()) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            val openIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            dueTasks.forEach { task ->
                val notification = NotificationCompat.Builder(
                    applicationContext,
                    NotificationScheduler.CHANNEL_ID,
                )
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(task.title)
                    .setContentText(applicationContext.getString(R.string.notification_task_today))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(openIntent)
                    .build()

                manager.notify(task.id.hashCode(), notification)
            }
        }

        NotificationScheduler.scheduleNext(applicationContext)
        return Result.success()
    }
}
