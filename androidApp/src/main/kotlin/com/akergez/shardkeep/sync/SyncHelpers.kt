package com.akergez.shardkeep.sync

import android.content.SharedPreferences
import android.content.Context
import com.akergez.shardkeep.R
import com.akergez.shardkeep.SyncStatusStore
import com.akergez.tresse.kmp.TresseSyncProgress

typealias TresseConfig = com.akergez.tresse.kmp.TresseConfig
typealias TresseSyncResult = com.akergez.tresse.kmp.TresseSyncResult

private const val KEY_SYNC_ENABLED = "sync_auto_enabled"
private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
private const val KEY_SYNC_DEBOUNCE = "sync_debounce_seconds"

internal fun TresseConfig.withLocalSync(prefs: SharedPreferences): TresseConfig = copy(
    autoSyncEnabled = prefs.getBoolean(KEY_SYNC_ENABLED, false),
    autoSyncIntervalMinutes = prefs.numberAsLong(KEY_SYNC_INTERVAL, autoSyncIntervalMinutes),
    autoSyncDebounceSeconds = prefs.numberAsLong(KEY_SYNC_DEBOUNCE, autoSyncDebounceSeconds),
)

/** Accepts both the old Int migration value and the canonical Long representation. */
private fun SharedPreferences.numberAsLong(key: String, default: Long): Long =
    (all[key] as? Number)?.toLong() ?: default

internal fun SharedPreferences.saveLocalSync(config: TresseConfig) {
    edit()
        .putBoolean(KEY_SYNC_ENABLED, config.autoSyncEnabled)
        .putLong(KEY_SYNC_INTERVAL, config.autoSyncIntervalMinutes)
        .putLong(KEY_SYNC_DEBOUNCE, config.autoSyncDebounceSeconds)
        .apply()
}

fun summaryOf(context: Context, result: TresseSyncResult): String =
    if (result.pushed == 0 && result.pulled == 0) {
        context.getString(R.string.sync_already_up_to_date)
    } else {
        context.getString(R.string.sync_result_summary, result.pushed, result.pulled)
    }

internal fun SyncStatusStore.asProgress(): TresseSyncProgress = object : TresseSyncProgress {
    override fun reportCommitRead() = this@asProgress.reportCommitRead()
    override fun reportUpload() = this@asProgress.reportUpload()
    override fun reportDownload() = this@asProgress.reportDownload()
    override fun reportApply() = this@asProgress.reportApply()
}
