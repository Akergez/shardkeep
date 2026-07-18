package com.akergez.shardkeep

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.akergez.shardkeep.data.NotesDirectoryQuickNoteRepository
import com.akergez.shardkeep.data.NotesDirectoryTaskRepository
import com.akergez.shardkeep.domain.model.TaskFormat
import com.akergez.shardkeep.platform.currentLocalDate
import com.akergez.tresse.kmp.TresseConfig
import com.akergez.tresse.kmp.TresseConfigString
import com.akergez.tresse.kmp.TresseSync
import com.akergez.tresse.kmp.TresseSyncProgress
import com.akergez.tresse.kmp.fileTresseVault
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences
import javax.swing.JFileChooser

fun main() {
    System.setProperty("apple.awt.application.appearance", "system")
    application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Shardkeep",
        icon = shardkeepAppIconPainter(),
    ) {
        DesktopShardkeepApp()
    }
    }
}

@Composable
private fun DesktopShardkeepApp() {
    DesktopMacOsTheme {
        val preferences = remember { Preferences.userNodeForPackage(DesktopNotesDirectoryStorage::class.java) }
        var rootPath by remember { mutableStateOf(preferences.get("notesDirectory", null)) }
        var format by remember {
            mutableStateOf(preferences.get("taskFormat", null)?.let { runCatching { TaskFormat.valueOf(it) }.getOrNull() })
        }
        val root = rootPath?.let(::File)
        if (root == null || !root.isDirectory) {
            NotesDirectorySetupScreen(onChooseNotesDirectory = {
                JFileChooser().apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Select notes directory"
                }.let { chooser ->
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        chooser.selectedFile.absolutePath.also {
                            preferences.put("notesDirectory", it)
                            rootPath = it
                        }
                    }
                }
            })
            return@DesktopMacOsTheme
        }
        if (format == null) {
            FormatSetupScreen(onFormatSelected = {
                preferences.put("taskFormat", it.name)
                format = it
            })
            return@DesktopMacOsTheme
        }

        val storage = remember(rootPath) { DesktopNotesDirectoryStorage(root) }
        val taskRepository = remember(storage, format) { NotesDirectoryTaskRepository(storage, checkNotNull(format)) }
        var quickNotesFolder by remember { mutableStateOf(preferences.get("quickNotesFolder", NotesDirectoryQuickNoteRepository.QUICK_NOTES_FOLDER)) }
        val quickNoteRepository = remember(storage, quickNotesFolder) {
            NotesDirectoryQuickNoteRepository(storage, quickNotesFolder)
        }
        val syncStore = remember { SyncStatusStore() }
        val syncMutex = remember { Mutex() }
        var syncConfig by remember(storage) { mutableStateOf<TresseConfig?>(null) }

        fun syncEngine(config: TresseConfig) = TresseSync(fileTresseVault(root) { ignoredPath(it, config) })
        suspend fun refresh() {
            taskRepository.refresh()
            quickNoteRepository.refresh()
        }
        suspend fun perform(forcePull: Boolean): String = syncMutex.withLock {
            val config = checkNotNull(syncConfig) { "Sync is not configured" }
            syncStore.begin("Desktop")
            return try {
                val engine = syncEngine(config)
                val result = if (forcePull) engine.forcePull(config, syncStore.progress())
                else engine.perform(config, syncStore.progress())
                refresh()
                summaryOf(result.pushed, result.pulled).also {
                    syncStore.finishOk(it, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
                }
            } catch (error: Throwable) {
                syncStore.finishError(error.message ?: "Sync error")
                throw error
            }
        }

        LaunchedEffect(storage, quickNoteRepository) {
            refresh()
            val engine = TresseSync(fileTresseVault(root))
            syncConfig = (engine.loadConfig() ?: TresseConfig(
                autoSyncEnabled = false,
                ignorePatterns = listOf(".git", ".trash", ".obsidian"),
            )).withLocalSync(preferences)
            syncConfig?.let { syncStore.updateConfig(it.storageType, it.isComplete(), it.autoSyncEnabled) }
        }

        LaunchedEffect(syncConfig) {
            val config = syncConfig ?: return@LaunchedEffect
            if (!config.autoSyncEnabled || !config.isComplete()) return@LaunchedEffect
            while (true) {
                delay(config.autoSyncIntervalMinutes.coerceAtLeast(1) * 60_000)
                runCatching { perform(false) }
            }
        }

        ShardkeepApp(
            repository = taskRepository,
            quickNoteRepository = quickNoteRepository,
            today = currentLocalDate(),
            settingsCallbacks = SettingsCallbacks(
                quickNotesFolder = quickNotesFolder,
                onFolderChange = {
                    preferences.put("quickNotesFolder", it)
                    quickNotesFolder = it
                },
                notificationsEnabled = false,
                onNotificationsEnabledChange = {},
                syncConfig = (syncConfig ?: TresseConfig(autoSyncEnabled = false)).toDraft(),
                onSyncConfigChange = { draft ->
                    val config = draft.toConfig()
                    syncEngine(config).saveConfig(config)
                    preferences.saveLocalSync(config)
                    syncConfig = config
                    syncStore.updateConfig(config.storageType, config.isComplete(), config.autoSyncEnabled)
                },
                onImportSyncConfig = { encoded ->
                    runCatching { TresseConfigString.decode(encoded) }.fold(
                        onSuccess = {
                            syncEngine(it).saveConfig(it)
                            preferences.saveLocalSync(it)
                            syncConfig = it
                            true
                        },
                        onFailure = { false },
                    )
                },
                onSync = { perform(false) },
                onForcePull = { perform(true) },
                syncState = syncStore.state,
            ),
        )
    }
}

private fun ignoredPath(path: String, config: TresseConfig): Boolean =
    path == ".tresse" || path.startsWith(".tresse/") ||
        config.ignorePatterns.any { it.isNotBlank() && (path == it || path.startsWith("$it/")) }

private fun summaryOf(pushed: Int, pulled: Int): String =
    if (pushed == 0 && pulled == 0) "Already up to date" else "Pushed $pushed, pulled $pulled"

private fun SyncStatusStore.progress() = object : TresseSyncProgress {
    override fun reportCommitRead() = this@progress.reportCommitRead()
    override fun reportUpload() = this@progress.reportUpload()
    override fun reportDownload() = this@progress.reportDownload()
    override fun reportApply() = this@progress.reportApply()
}

private fun TresseConfig.toDraft() = SyncConfigDraft(
    serverUrl, repoId, token, encryptionKey, storageType, s3Endpoint, s3Region, s3Bucket,
    s3AccessKeyId, s3SecretAccessKey, autoSyncEnabled, autoSyncIntervalMinutes,
    autoSyncDebounceSeconds, ignorePatterns,
)

private fun SyncConfigDraft.toConfig() = TresseConfig(
    serverUrl, repoId, token, encryptionKey, storageType, s3Endpoint, s3Region, s3Bucket,
    s3AccessKeyId, s3SecretAccessKey, ignorePatterns, autoSyncEnabled,
    autoSyncIntervalMinutes, autoSyncDebounceSeconds,
)

private fun TresseConfig.withLocalSync(preferences: Preferences) = copy(
    autoSyncEnabled = preferences.getBoolean("syncAutoEnabled", false),
    autoSyncIntervalMinutes = preferences.getLong("syncIntervalMinutes", autoSyncIntervalMinutes),
    autoSyncDebounceSeconds = preferences.getLong("syncDebounceSeconds", autoSyncDebounceSeconds),
)

private fun Preferences.saveLocalSync(config: TresseConfig) {
    putBoolean("syncAutoEnabled", config.autoSyncEnabled)
    putLong("syncIntervalMinutes", config.autoSyncIntervalMinutes)
    putLong("syncDebounceSeconds", config.autoSyncDebounceSeconds)
}
