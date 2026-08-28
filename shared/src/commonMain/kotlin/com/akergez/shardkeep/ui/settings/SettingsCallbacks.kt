package com.akergez.shardkeep.ui.settings

import com.akergez.shardkeep.SyncState
import kotlinx.coroutines.flow.StateFlow

data class SettingsCallbacks(
    val quickNotesFolder: String,
    val onFolderChange: (String) -> Unit,
    val notificationsEnabled: Boolean,
    val onNotificationsEnabledChange: (Boolean) -> Unit,
    val syncConfig: SyncConfigDraft? = null,
    val syncConfigured: Boolean = false,
    val onSyncConfigChange: suspend (SyncConfigDraft) -> Unit = { },
    val onImportSyncConfig: suspend (String) -> Boolean = { false },
    /** Runs a sync and returns a short human-readable summary; throws on failure. */
    val onSync: (suspend () -> String)? = null,
    /** Pulls remote state into an uninitialized local vault without committing first. */
    val onForcePull: (suspend () -> String)? = null,
    /**
     * Drops the local chunk store and clears the repository id and encryption key
     * from the config, leaving the notes and the remote store alone.
     */
    val onWipeChunks: (suspend () -> Unit)? = null,
    /** Live sync status (manual + background); when null, the status panel is hidden. */
    val syncState: StateFlow<SyncState>? = null,
    /** Whether this platform can put events into a calendar at all; hides the section when false. */
    val calendarAvailable: Boolean = false,
    val calendarSyncEnabled: Boolean = false,
    val onCalendarSyncEnabledChange: (Boolean) -> Unit = { },
    /** Runs a calendar export and returns a short summary; throws on failure. */
    val onCalendarSyncNow: (suspend () -> String)? = null,
    /** Writes an .ics file; returns a summary, or null when the user backed out of the picker. */
    val onCalendarExport: (suspend () -> String?)? = null,
    /** Human-readable moment of the last successful export; null before the first one. */
    val calendarLastSync: String? = null,
)
