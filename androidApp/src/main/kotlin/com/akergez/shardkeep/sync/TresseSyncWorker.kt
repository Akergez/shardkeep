package com.akergez.shardkeep.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.akergez.shardkeep.R
import com.akergez.shardkeep.notesdirectory.AndroidSafNotesDirectoryStorage
import com.akergez.tresse.kmp.TresseSync
import com.akergez.tresse.kmp.safTresseVault

class TresseSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val treeUri = prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse) ?: return Result.success()
        return try {
            val storage = AndroidSafNotesDirectoryStorage(applicationContext, treeUri)
            val config = (TresseConfigFile.read(storage) ?: return Result.success())
                .withLocalSync(prefs)
            if (!config.isComplete()) return Result.success()
            if (!config.autoSyncEnabled && !inputData.getBoolean(KEY_FORCE, false)) return Result.success()
            SyncStatusHolder.store.begin(applicationContext.getString(R.string.sync_trigger_background))
            val vault = safTresseVault(applicationContext, treeUri) { ignoredPath(it, config) }
            val result = TresseSync(vault).perform(config, SyncStatusHolder.store.asProgress())
            SyncStatusHolder.store.finishOk(summaryOf(result), SyncStatusHolder.nowLabel())
            Result.success()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            android.util.Log.e("TresseSync", "Sync failed (attempt $runAttemptCount)", error)
            SyncStatusHolder.store.finishError(
                error.message?.takeIf { it.isNotBlank() } ?: applicationContext.getString(R.string.sync_error_background),
            )
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val PREFS = "notes_directory"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_FORCE = "force"
        private const val MAX_RETRIES = 3
    }
}
