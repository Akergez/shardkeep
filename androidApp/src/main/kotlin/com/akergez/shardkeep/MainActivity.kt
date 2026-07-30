package com.akergez.shardkeep

import android.Manifest
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.glance.appwidget.updateAll
import com.akergez.shardkeep.R
import com.akergez.shardkeep.SettingsCallbacks
import com.akergez.shardkeep.data.NotesDirectoryTaskRepository
import com.akergez.shardkeep.data.NotesDirectoryQuickNoteRepository
import com.akergez.shardkeep.db.DatabaseDriverFactory
import com.akergez.shardkeep.db.createDatabase
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.model.TaskFormat
import com.akergez.shardkeep.platform.currentLocalDate
import com.akergez.shardkeep.notesdirectory.AndroidSafNotesDirectoryStorage
import com.akergez.shardkeep.notifications.NotificationScheduler
import com.akergez.shardkeep.widget.ChecklistWidget
import com.akergez.shardkeep.widget.QuickNoteWidget
import com.akergez.shardkeep.widget.TaskWidget
import com.akergez.shardkeep.widget.WidgetNavigation
import com.akergez.shardkeep.widget.WidgetUpdatingQuickNoteRepository
import com.akergez.shardkeep.widget.WidgetUpdatingTaskRepository
import com.akergez.shardkeep.sync.TresseConfig
import com.akergez.tresse.kmp.TresseConfigString
import com.akergez.tresse.kmp.TresseCapabilities
import com.akergez.tresse.kmp.TresseSync
import com.akergez.tresse.kmp.safTresseVault
import com.akergez.shardkeep.sync.TresseSyncScheduler
import com.akergez.shardkeep.sync.SyncStatusHolder
import com.akergez.shardkeep.sync.asProgress
import com.akergez.shardkeep.sync.ignoredPath
import com.akergez.shardkeep.sync.summaryOf
import com.akergez.shardkeep.sync.saveLocalSync
import com.akergez.shardkeep.sync.withLocalSync
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    private val pendingDeepLink = mutableStateOf<AppDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingDeepLink.value = parseDeepLink(intent)
        NotificationScheduler.createChannel(this)
        WorkspaceRegistry(this).list().forEach { workspace ->
            NotificationScheduler.scheduleNext(this, workspace.id)
        }
        // These remote/credential fields live in tresse.toml. The [sync] fields
        // (sync_auto_enabled / sync_interval_minutes / sync_debounce_seconds) are
        // device-local and intentionally kept in SharedPreferences.
        getSharedPreferences("notes_directory", Context.MODE_PRIVATE).edit()
            .remove("sync_server_url")
            .remove("sync_repo_id")
            .remove("sync_token")
            .remove("sync_key")
            .remove("sync_ignore_patterns")
            .apply()

        setContent {
            ShardkeepTheme {
                AndroidShardkeepApp(pendingDeepLink)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDeepLink(intent)?.let { pendingDeepLink.value = it }
    }

    private fun parseDeepLink(intent: Intent?): AppDeepLink? {
        intent ?: return null
        return when (intent.getStringExtra(WidgetNavigation.EXTRA_NAV)) {
            WidgetNavigation.NAV_TASKS -> AppDeepLink.Tasks
            WidgetNavigation.NAV_NOTES -> AppDeepLink.Notes
            WidgetNavigation.NAV_NOTE ->
                intent.getStringExtra(WidgetNavigation.EXTRA_NOTE_PATH)
                    ?.let { AppDeepLink.OpenNote(it) }
            else -> null
        }
    }
}

@Composable
private fun MainActivity.AndroidShardkeepApp(pendingDeepLink: MutableState<AppDeepLink?>) {
    val workspaceRegistry = remember { WorkspaceRegistry(applicationContext) }
    var workspaceRecords by remember { mutableStateOf(workspaceRegistry.list()) }
    var activeWorkspaceId by remember { mutableStateOf(workspaceRegistry.active()?.id) }
    val activeWorkspace = workspaceRecords.firstOrNull { it.id == activeWorkspaceId }
        ?: workspaceRecords.firstOrNull()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val name = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.workspace_numbered, workspaceRecords.size + 1)
        val added = workspaceRegistry.add(name, uri)
        workspaceRecords = workspaceRegistry.list()
        activeWorkspaceId = added.id
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(activeWorkspace?.id) {
        if (activeWorkspace == null) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        NotificationScheduler.scheduleNext(applicationContext, activeWorkspace.id)
    }

    if (activeWorkspace == null) {
        NotesDirectorySetupScreen(onChooseNotesDirectory = { picker.launch(null) })
        return
    }
    val uri = activeWorkspace.treeUri
    val preferences = remember(activeWorkspace.id) { workspaceRegistry.preferences(activeWorkspace.id) }
    val syncStatus = remember(activeWorkspace.id) { SyncStatusHolder.storeFor(activeWorkspace.id) }

    val format = activeWorkspace.taskFormat
    if (format == null) {
        FormatSetupScreen(onFormatSelected = { chosen ->
            workspaceRegistry.update(activeWorkspace.copy(taskFormat = chosen))
            workspaceRecords = workspaceRegistry.list()
        })
        return
    }

    var quickNotesFolder by remember {
        mutableStateOf(
            preferences.getString("quick_notes_folder", null)
                ?: NotesDirectoryQuickNoteRepository.QUICK_NOTES_FOLDER
        )
    }
    var notificationsEnabled by remember {
        mutableStateOf(preferences.getBoolean("notifications_enabled", true))
    }
    val database = remember { createDatabase(DatabaseDriverFactory(applicationContext)) }
    val storage = remember(uri) { AndroidSafNotesDirectoryStorage(applicationContext, uri) }
    val configSync = remember(uri) { TresseSync(safTresseVault(applicationContext, uri)) }
    var syncConfig by remember(storage) { mutableStateOf<TresseConfig?>(null) }
    val taskSource = remember(storage, format) { NotesDirectoryTaskRepository(storage, format, database = database) }
    val repository = remember(taskSource) { WidgetUpdatingTaskRepository(taskSource, applicationContext) }
    val quickNoteSource = remember(storage, quickNotesFolder) { NotesDirectoryQuickNoteRepository(storage, quickNotesFolder) }
    val quickNoteRepository = remember(quickNoteSource) {
        WidgetUpdatingQuickNoteRepository(quickNoteSource, applicationContext)
    }
    var loadError by remember(repository) { mutableStateOf(false) }
    var refreshKey by remember(repository) { mutableStateOf(0) }
    val today = remember { currentLocalDate() }

    LaunchedEffect(storage, refreshKey) {
        val config = runCatching { configSync.loadConfig() }.getOrNull()
            ?.withLocalSync(preferences)
        syncConfig = config
        pushSyncStatus(syncStatus, config)
        TresseSyncScheduler.schedule(applicationContext, activeWorkspace.id, config)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var isFirstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isFirstResume) isFirstResume = false else refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(repository, quickNoteRepository, refreshKey) {
        loadError = false
        runCatching {
            repository.refresh()
            quickNoteRepository.refresh()
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            loadError = true
        }
    }

    if (loadError) {
        NotesDirectoryErrorScreen(
            onRetry = { refreshKey += 1 },
            onChooseAnother = {
                picker.launch(null)
            },
        )
    } else {
        ShardkeepApp(
            repository = repository,
            today = today,
            quickNoteRepository = quickNoteRepository,
            deepLink = pendingDeepLink.value,
            onDeepLinkHandled = { pendingDeepLink.value = null },
            workspaceName = activeWorkspace.name,
            workspaces = workspaceRecords.map { WorkspaceOption(it.id, it.name) },
            onWorkspaceSelected = { id ->
                workspaceRegistry.select(id)
                activeWorkspaceId = id
            },
            onAddWorkspace = { picker.launch(null) },
            settingsCallbacks = SettingsCallbacks(
                quickNotesFolder = quickNotesFolder,
                onFolderChange = { newFolder ->
                    preferences.edit().putString("quick_notes_folder", newFolder).apply()
                    quickNotesFolder = newFolder
                },
                notificationsEnabled = notificationsEnabled,
                onNotificationsEnabledChange = { enabled ->
                    preferences.edit().putBoolean("notifications_enabled", enabled).apply()
                    notificationsEnabled = enabled
                    if (enabled) NotificationScheduler.scheduleNext(applicationContext, activeWorkspace.id)
                    else NotificationScheduler.cancel(applicationContext, activeWorkspace.id)
                },
                syncConfig = syncConfig?.toDraft(),
                supportedLanguages = TresseCapabilities.supportedLanguages().map { language ->
                    SyncLanguageOption(
                        language.id,
                        language.description,
                        language.extensions,
                        language.strategies.map { it.id },
                    )
                },
                onSyncConfigChange = { draft ->
                    val updated = draft.toTresseConfig()
                    preferences.saveLocalSync(updated)
                    configSync.saveConfig(updated)
                    syncConfig = updated
                    pushSyncStatus(syncStatus, updated)
                    TresseSyncScheduler.schedule(applicationContext, activeWorkspace.id, updated)
                },
                onImportSyncConfig = { configString ->
                    runCatching { TresseConfigString.decode(configString) }.fold(
                        onSuccess = { imported ->
                            preferences.saveLocalSync(imported)
                            configSync.saveConfig(imported)
                            syncConfig = imported
                            pushSyncStatus(syncStatus, imported)
                            TresseSyncScheduler.schedule(applicationContext, activeWorkspace.id, imported)
                            true
                        },
                        onFailure = { false },
                    )
                },
                onSync = {
                    syncStatus.begin(getString(R.string.sync_trigger_manual))
                    try {
                        val config = requireNotNull(configSync.loadConfig()) { getString(R.string.config_not_found) }
                            .withLocalSync(preferences)
                        val vault = safTresseVault(applicationContext, uri) { ignoredPath(it, config) }
                        val result = TresseSync(vault).perform(config, syncStatus.asProgress())
                        repository.refresh()
                        quickNoteRepository.refresh()
                        val summary = summaryOf(applicationContext, result)
                        syncStatus.finishOk(summary, SyncStatusHolder.nowLabel())
                        summary
                    } catch (error: Throwable) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        syncStatus.finishError(
                            error.message?.takeIf { it.isNotBlank() } ?: getString(R.string.sync_error_generic),
                        )
                        throw error
                    }
                },
                onForcePull = {
                    syncStatus.begin(getString(R.string.sync_trigger_manual))
                    try {
                        val config = requireNotNull(configSync.loadConfig()) { getString(R.string.config_not_found) }
                            .withLocalSync(preferences)
                        val vault = safTresseVault(applicationContext, uri) { ignoredPath(it, config) }
                        val result = TresseSync(vault).forcePull(config, syncStatus.asProgress())
                        repository.refresh()
                        quickNoteRepository.refresh()
                        val summary = summaryOf(applicationContext, result)
                        syncStatus.finishOk(summary, SyncStatusHolder.nowLabel())
                        summary
                    } catch (error: Throwable) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        syncStatus.finishError(
                            error.message?.takeIf { it.isNotBlank() } ?: getString(R.string.sync_error_generic),
                        )
                        throw error
                    }
                },
                syncState = syncStatus.state,
            ),
        )
    }
}


/** Reflect the currently saved config in the sync status panel's storage line. */
private fun pushSyncStatus(store: SyncStatusStore, config: TresseConfig?) {
    store.updateConfig(
        storageType = config?.storageType ?: "http",
        configured = config?.isComplete() == true,
        autoSyncEnabled = config?.autoSyncEnabled == true,
    )
}

private fun TresseConfig.toDraft() = SyncConfigDraft(
    serverUrl = serverUrl,
    repoId = repoId,
    token = token,
    encryptionKey = encryptionKey,
    storageType = storageType,
    s3Endpoint = s3Endpoint,
    s3Region = s3Region,
    s3Bucket = s3Bucket,
    s3AccessKeyId = s3AccessKeyId,
    s3SecretAccessKey = s3SecretAccessKey,
    autoSyncEnabled = autoSyncEnabled,
    autoSyncIntervalMinutes = autoSyncIntervalMinutes,
    autoSyncDebounceSeconds = autoSyncDebounceSeconds,
    ignorePatterns = ignorePatterns,
    languages = languages,
)

private fun SyncConfigDraft.toTresseConfig() = TresseConfig(
    serverUrl = serverUrl,
    repoId = repoId,
    token = token,
    encryptionKey = encryptionKey,
    storageType = storageType,
    s3Endpoint = s3Endpoint,
    s3Region = s3Region,
    s3Bucket = s3Bucket,
    s3AccessKeyId = s3AccessKeyId,
    s3SecretAccessKey = s3SecretAccessKey,
    ignorePatterns = ignorePatterns,
    languages = languages,
    autoSyncEnabled = autoSyncEnabled,
    autoSyncIntervalMinutes = autoSyncIntervalMinutes,
    autoSyncDebounceSeconds = autoSyncDebounceSeconds,
)

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
