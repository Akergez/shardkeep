package com.akergez.shardkeep.sync

import com.akergez.shardkeep.SyncStatusStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Process-wide sync status. WorkManager runs in the app's main process, so the
 * background [TresseSyncWorker] and the foreground Settings screen observe and
 * drive this single store — a background sync updates the same panel the user
 * sees after tapping "Sync now".
 */
object SyncStatusHolder {
    val store = SyncStatusStore()

    /** Local wall-clock label for a just-finished sync, e.g. "14:30". */
    fun nowLabel(): String = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date())
}
