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
    private const val PERIODIC_WORK = "tresse-periodic-sync"
    private const val IMMEDIATE_WORK = "tresse-immediate-sync"

    fun schedule(context: Context, config: TresseConfig?) {
        val prefs = context.getSharedPreferences(TresseSyncWorker.PREFS, Context.MODE_PRIVATE)
        val configured = prefs.getString(TresseSyncWorker.KEY_TREE_URI, null) != null &&
            config != null && config.autoSyncEnabled && config.isComplete()
        val manager = WorkManager.getInstance(context)
        if (!configured) {
            manager.cancelUniqueWork(PERIODIC_WORK)
            manager.cancelUniqueWork(IMMEDIATE_WORK)
            return
        }

        val interval = config!!.autoSyncIntervalMinutes.coerceAtLeast(15)
        val flex = (interval / 3).coerceIn(5, interval)
        val request = PeriodicWorkRequestBuilder<TresseSyncWorker>(interval, TimeUnit.MINUTES, flex, TimeUnit.MINUTES)
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<TresseSyncWorker>()
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(TresseSyncWorker.KEY_FORCE to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun networkConstraint() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
