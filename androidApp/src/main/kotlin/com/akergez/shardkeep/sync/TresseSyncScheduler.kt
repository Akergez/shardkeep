package com.akergez.shardkeep.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object TresseSyncScheduler {
    private const val KEY_WORKSPACE_ID = "workspace_id"

    fun schedule(context: Context, workspaceId: String, config: TresseConfig?) {
        val configured = config != null && config.autoSyncEnabled && config.isComplete()
        val manager = WorkManager.getInstance(context)
        if (!configured) {
            manager.cancelUniqueWork(periodicWork(workspaceId))
            manager.cancelUniqueWork(immediateWork(workspaceId))
            return
        }

        val interval = config!!.autoSyncIntervalMinutes.coerceAtLeast(15)
        val flex = (interval / 3).coerceIn(5, interval)
        val request = PeriodicWorkRequestBuilder<TresseSyncWorker>(interval, TimeUnit.MINUTES, flex, TimeUnit.MINUTES)
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_WORKSPACE_ID to workspaceId))
            .build()
        manager.enqueueUniquePeriodicWork(periodicWork(workspaceId), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun syncNow(context: Context, workspaceId: String) {
        val request = OneTimeWorkRequestBuilder<TresseSyncWorker>()
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(TresseSyncWorker.KEY_FORCE to true, KEY_WORKSPACE_ID to workspaceId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(immediateWork(workspaceId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun networkConstraint() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    internal fun workspaceId(data: androidx.work.Data): String? = data.getString(KEY_WORKSPACE_ID)
    private fun periodicWork(id: String) = "tresse-periodic-sync-$id"
    private fun immediateWork(id: String) = "tresse-immediate-sync-$id"
}
