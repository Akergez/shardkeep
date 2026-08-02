package com.akergez.shardkeep.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.akergez.shardkeep.R
import com.akergez.shardkeep.WorkspaceRegistry
import com.akergez.shardkeep.widget.WidgetUpdater
import com.akergez.tresse.kmp.TresseSync
import com.akergez.tresse.kmp.safTresseVault

class TresseSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val workspaceId = TresseSyncScheduler.workspaceId(inputData) ?: return Result.success()
        val registry = WorkspaceRegistry(applicationContext)
        val workspace = registry.list().firstOrNull { it.id == workspaceId } ?: return Result.success()
        val prefs = registry.preferences(workspaceId)
        val treeUri = workspace.treeUri
        val status = SyncStatusHolder.storeFor(workspaceId)
        return try {
            val configVault = safTresseVault(applicationContext, treeUri)
            val config = (TresseSync(configVault).loadConfig() ?: return Result.success())
                .withLocalSync(prefs)
            if (!config.isComplete()) return Result.success()
            if (!config.autoSyncEnabled && !inputData.getBoolean(KEY_FORCE, false)) return Result.success()
            status.begin(applicationContext.getString(R.string.sync_trigger_background))
            val vault = safTresseVault(applicationContext, treeUri, ignoreFilter(config))
            val result = TresseSync(vault).perform(config, status.asProgress())
            WidgetUpdater.refreshAll(applicationContext)
            status.finishOk(summaryOf(applicationContext, result), SyncStatusHolder.nowLabel())
            Result.success()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            android.util.Log.e("TresseSync", "Sync failed (attempt $runAttemptCount)", error)
            status.finishError(
                error.message?.takeIf { it.isNotBlank() } ?: applicationContext.getString(R.string.sync_error_background),
            )
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_FORCE = "force"
        private const val MAX_RETRIES = 3
    }
}
