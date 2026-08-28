package com.akergez.shardkeep.calendar

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Расписание выгрузки в календарь. Автоматический прогон — периодическая работа
 * WorkManager; кнопка в настройках синхронизирует сразу и сама, минуя очередь, а
 * [syncNow] остаётся для мест, где ждать результата некому.
 */
object CalendarSyncScheduler {
    const val PREF_ENABLED = "calendar_sync_enabled"
    const val PREF_LAST_SYNC = "calendar_last_sync"

    private const val KEY_WORKSPACE_ID = "workspace_id"
    // Минимум, который WorkManager вообще принимает для периодической работы.
    private const val INTERVAL_MINUTES = 60L

    fun schedule(context: Context, workspaceId: String, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(periodicWork(workspaceId))
            return
        }
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
            INTERVAL_MINUTES,
            TimeUnit.MINUTES,
            INTERVAL_MINUTES / 4,
            TimeUnit.MINUTES,
        )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_WORKSPACE_ID to workspaceId))
            .build()
        manager.enqueueUniquePeriodicWork(
            periodicWork(workspaceId),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun syncNow(context: Context, workspaceId: String) {
        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_WORKSPACE_ID to workspaceId))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(immediateWork(workspaceId), ExistingWorkPolicy.REPLACE, request)
    }

    internal fun workspaceId(data: Data): String? = data.getString(KEY_WORKSPACE_ID)

    private fun periodicWork(id: String) = "shardkeep-calendar-sync-$id"
    private fun immediateWork(id: String) = "shardkeep-calendar-sync-now-$id"
}
