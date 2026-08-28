package com.akergez.shardkeep.calendar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.akergez.shardkeep.WorkspaceRegistry
import com.akergez.shardkeep.data.NotesDirectoryTaskRepository
import com.akergez.shardkeep.domain.model.toCalendarEvents
import com.akergez.shardkeep.notesdirectory.AndroidSafNotesDirectoryStorage
import kotlinx.coroutines.flow.first

/**
 * Фоновая выгрузка событий в календарь. Разрешение здесь не спрашивают — его даёт
 * экран настроек; без него работа просто заканчивается ничем и ждёт следующего раза.
 */
class CalendarSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val workspaceId = CalendarSyncScheduler.workspaceId(inputData) ?: return Result.success()
        val registry = WorkspaceRegistry(applicationContext)
        val workspace = registry.list().firstOrNull { it.id == workspaceId } ?: return Result.success()
        val prefs = registry.preferences(workspaceId)
        if (!prefs.getBoolean(CalendarSyncScheduler.PREF_ENABLED, false)) return Result.success()
        if (!AndroidCalendarSync.hasPermission(applicationContext)) return Result.success()
        val format = workspace.taskFormat ?: return Result.success()

        return try {
            val repository = NotesDirectoryTaskRepository(
                AndroidSafNotesDirectoryStorage(applicationContext, workspace.treeUri),
                format,
            )
            repository.refresh()
            val events = repository.observeTasks().first().toCalendarEvents()
            AndroidCalendarSync.sync(applicationContext, workspaceId, workspace.name, events)
            prefs.edit().putLong(CalendarSyncScheduler.PREF_LAST_SYNC, System.currentTimeMillis()).apply()
            Result.success()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            android.util.Log.e("CalendarSync", "Calendar sync failed (attempt $runAttemptCount)", error)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
