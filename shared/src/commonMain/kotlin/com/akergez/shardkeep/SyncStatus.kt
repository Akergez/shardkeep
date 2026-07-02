package com.akergez.shardkeep

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Coarse stage of a sync, ordered to match the engine's commit → sync → restore
 * pipeline. Android runs the whole flow inside one `casSync` FFI call, but the
 * engine drives Kotlin callbacks as it works, so each stage is detected from a
 * real signal: reading the notes directory (commit), pushing objects (sync), writing the
 * notes directory (restore). Stages only ever move forward — [rank] enforces that.
 */
enum class SyncPhase(val rank: Int) {
    Idle(0),
    Preparing(1),
    Committing(2),   // reading local files to snapshot
    Transferring(3), // exchanging encrypted objects with the remote
    Applying(4),     // writing merged state back into the notes directory
    Done(5),
    Error(6),
}

/**
 * Full observable state of sync, shared by the manual "Sync now" button and the
 * background worker so both surface identically. Counters are live: they tick up
 * as the engine exercises each callback surface during a single sync.
 */
data class SyncState(
    val phase: SyncPhase = SyncPhase.Idle,
    /** What kicked off the running/last sync, e.g. "Ручная" / "Фоновая". */
    val trigger: String? = null,
    /** Local files read for the commit snapshot. */
    val committed: Int = 0,
    /** Encrypted objects pushed to the remote. */
    val uploaded: Int = 0,
    /** Encrypted objects pulled from the remote. */
    val downloaded: Int = 0,
    /** Notes-directory files written/removed while materializing the merged state. */
    val applied: Int = 0,
    /** Summary of the last successful sync, e.g. "Отправлено 3, получено 1". */
    val lastResult: String? = null,
    /** Message of the last failed sync. */
    val error: String? = null,
    /** Preformatted local time of the last successful sync (e.g. "14:30"). */
    val lastSyncLabel: String? = null,
    val storageType: String = "http",
    val configured: Boolean = false,
    val autoSyncEnabled: Boolean = false,
) {
    val active: Boolean get() = phase.rank in SyncPhase.Preparing.rank..SyncPhase.Applying.rank
}

/**
 * Single-operation state machine shared by the host (which drives it, including
 * per-object progress) and the Settings UI (which observes it). One sync runs at
 * a time; the store is a process-wide singleton on the host side so background
 * and foreground syncs write to the same state.
 */
class SyncStatusStore {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Mark a sync as started, resetting counters and clearing any previous error. */
    fun begin(trigger: String?) {
        _state.update {
            it.copy(
                phase = SyncPhase.Preparing,
                trigger = trigger,
                committed = 0,
                uploaded = 0,
                downloaded = 0,
                applied = 0,
                error = null,
            )
        }
    }

    /** A local file was read for the commit snapshot. */
    fun reportCommitRead() = _state.update {
        it.copy(phase = it.phase.atLeast(SyncPhase.Committing), committed = it.committed + 1)
    }

    /** An encrypted object was pushed. Only ever happens in the sync stage. */
    fun reportUpload() = _state.update {
        it.copy(phase = it.phase.atLeast(SyncPhase.Transferring), uploaded = it.uploaded + 1)
    }

    /** An encrypted object was pulled. Pulls span several stages, so they only
     *  bump the counter and never drive the phase. */
    fun reportDownload() = _state.update { it.copy(downloaded = it.downloaded + 1) }

    /** A notes-directory file was written/removed while restoring the merged state. */
    fun reportApply() = _state.update {
        it.copy(phase = it.phase.atLeast(SyncPhase.Applying), applied = it.applied + 1)
    }

    /** Mark the running sync finished successfully. */
    fun finishOk(summary: String, at: String?) = _state.update {
        it.copy(
            phase = SyncPhase.Done,
            lastResult = summary,
            error = null,
            lastSyncLabel = at ?: it.lastSyncLabel,
        )
    }

    /** Mark the running sync failed. */
    fun finishError(message: String) = _state.update {
        it.copy(phase = SyncPhase.Error, error = message)
    }

    /** Reflect the currently saved config in the storage status line. */
    fun updateConfig(storageType: String, configured: Boolean, autoSyncEnabled: Boolean) = _state.update {
        it.copy(storageType = storageType, configured = configured, autoSyncEnabled = autoSyncEnabled)
    }

    private fun SyncPhase.atLeast(other: SyncPhase): SyncPhase = if (other.rank > rank) other else this
}
