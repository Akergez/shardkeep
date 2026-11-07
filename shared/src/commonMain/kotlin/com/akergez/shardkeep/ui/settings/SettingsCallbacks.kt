package com.akergez.shardkeep.ui.settings

import com.akergez.shardkeep.SyncState
import com.akergez.shardkeep.sync.ServerConnectEvent
import com.akergez.shardkeep.sync.ServerConnectFailure
import kotlinx.coroutines.flow.StateFlow

data class SettingsCallbacks(
    val quickNotesFolder: String,
    val onFolderChange: (String) -> Unit,
    val notificationsEnabled: Boolean,
    val onNotificationsEnabledChange: (Boolean) -> Unit,
    val syncConfig: SyncConfigDraft? = null,
    val syncConfigured: Boolean = false,
    val onSyncConfigChange: suspend (SyncConfigDraft) -> Unit = { },
    /**
     * Imports a `tresse://` string or a `tresse.toml` body into this replica's config;
     * null on success, otherwise why it was refused.
     */
    val onImportSyncConfig: suspend (String) -> ServerConnectFailure? = { ServerConnectFailure.Decode },
    /** The complete config as a shareable `tresse://` string; null until sync is fully set up. */
    val syncConfigString: String? = null,
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
    /** Default Tresse server baked in at build time (TRESSE_SERVER_URL); may be blank. */
    val serverConnectUrl: String = "",
    /**
     * Opens the connect page of the given server in a browser; false when the address
     * was refused (not https) or the browser could not be opened.
     */
    val onServerConnectBegin: (suspend (server: String) -> Boolean)? = null,
    /** Verified server return waiting for the user; resolved via [onServerConnectResolve]. */
    val serverConnectEvent: ServerConnectEvent? = null,
    /** true applies the pending config, false or a dismissed dialog drops it. */
    val onServerConnectResolve: (accept: Boolean) -> Unit = { },
)
