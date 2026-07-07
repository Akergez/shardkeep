package com.akergez.shardkeep.sync

import android.content.SharedPreferences
import com.akergez.shardkeep.SyncStatusStore
import com.akergez.tresse.kmp.TresseSyncProgress

typealias TresseConfig = com.akergez.tresse.kmp.TresseConfig
typealias TresseSyncResult = com.akergez.tresse.kmp.TresseSyncResult

private const val KEY_SYNC_ENABLED = "sync_auto_enabled"
private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
private const val KEY_SYNC_DEBOUNCE = "sync_debounce_seconds"

internal fun TresseConfig.withLocalSync(prefs: SharedPreferences): TresseConfig = copy(
    autoSyncEnabled = prefs.getBoolean(KEY_SYNC_ENABLED, false),
    autoSyncIntervalMinutes = prefs.getLong(KEY_SYNC_INTERVAL, autoSyncIntervalMinutes),
    autoSyncDebounceSeconds = prefs.getLong(KEY_SYNC_DEBOUNCE, autoSyncDebounceSeconds),
)

internal fun SharedPreferences.saveLocalSync(config: TresseConfig) {
    edit()
        .putBoolean(KEY_SYNC_ENABLED, config.autoSyncEnabled)
        .putLong(KEY_SYNC_INTERVAL, config.autoSyncIntervalMinutes)
        .putLong(KEY_SYNC_DEBOUNCE, config.autoSyncDebounceSeconds)
        .apply()
}

fun summaryOf(result: TresseSyncResult): String =
    if (result.pushed == 0 && result.pulled == 0) "Уже актуально"
    else "Отправлено ${result.pushed}, получено ${result.pulled}"

internal fun ignoredPath(path: String, config: TresseConfig): Boolean =
    path == ".tresse" || path.startsWith(".tresse/") || path.split('/').any { it.startsWith(".") } ||
        config.ignorePatterns.any { it.isNotBlank() && (path == it || path.startsWith("$it/")) }

internal fun SyncStatusStore.asProgress(): TresseSyncProgress = object : TresseSyncProgress {
    override fun reportCommitRead() = this@asProgress.reportCommitRead()
    override fun reportUpload() = this@asProgress.reportUpload()
    override fun reportDownload() = this@asProgress.reportDownload()
    override fun reportApply() = this@asProgress.reportApply()
}
