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
import com.akergez.shardkeep.WorkspaceRegistry
import com.akergez.shardkeep.data.NotesDirectoryTaskRepository
import com.akergez.shardkeep.notesdirectory.AndroidSafNotesDirectoryStorage
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val workspaceId = inputData.getString(NotificationScheduler.KEY_WORKSPACE_ID) ?: return Result.success()
        val registry = WorkspaceRegistry(applicationContext)
        val workspace = registry.list().firstOrNull { it.id == workspaceId } ?: return Result.success()
        val prefs = registry.preferences(workspaceId)
        if (!prefs.getBoolean("notifications_enabled", true)) {
            return Result.success()
        }
        val format = workspace.taskFormat ?: return Result.success()
        val repository = NotesDirectoryTaskRepository(
            AndroidSafNotesDirectoryStorage(applicationContext, workspace.treeUri),
            format,
        )
        runCatching { repository.refresh() }.getOrElse { return Result.retry() }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val dueTasks = repository.observeTasks().first().filter { task ->
            task.dueDate?.toString() == today && task.status.name != "Done"
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

                manager.notify("$workspaceId:${task.id.value}".hashCode(), notification)
            }
        }

        NotificationScheduler.scheduleNext(applicationContext, workspaceId)
        return Result.success()
    }
}
