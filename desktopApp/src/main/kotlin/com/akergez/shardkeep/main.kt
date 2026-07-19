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
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.*
import com.akergez.tresse.kmp.TresseConfig
import com.akergez.tresse.kmp.TresseConfigString
import com.akergez.tresse.kmp.TresseSync
import com.akergez.tresse.kmp.TresseSyncProgress
import com.akergez.tresse.kmp.fileTresseVault
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.awt.Taskbar
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import java.util.UUID
import javax.imageio.ImageIO

fun main() {
    configureMacOsApplication()
    application {
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        icon = shardkeepAppIconPainter(),
    ) {
        DesktopShardkeepApp()
    }
    }
}

private fun configureMacOsApplication() {
    if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return
    System.setProperty("apple.awt.application.appearance", "system")
    System.setProperty("apple.awt.application.name", "Shardkeep")
    runCatching {
        if (!Taskbar.isTaskbarSupported() ||
            !Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)
        ) return@runCatching
        val iconUrl = Thread.currentThread().contextClassLoader.getResource("icon.png")
            ?: return@runCatching
        Taskbar.getTaskbar().iconImage = ImageIO.read(iconUrl)
    }
}

@Composable
private fun DesktopShardkeepApp() {
    DesktopMacOsTheme {
        val defaultWorkspaceName = stringResource(Res.string.workspace_default)
        val selectDirectoryTitle = stringResource(Res.string.select_notes_directory)
        val syncNotConfiguredError = stringResource(Res.string.sync_not_configured_error)
        val syncTriggerDesktop = stringResource(Res.string.sync_trigger_desktop)
        val syncErrorGeneric = stringResource(Res.string.sync_error_generic)
        val registryPreferences = remember { Preferences.userNodeForPackage(DesktopNotesDirectoryStorage::class.java) }
        var workspaces by remember(defaultWorkspaceName) {
            mutableStateOf(loadDesktopWorkspaces(registryPreferences, defaultWorkspaceName))
        }
        var activeWorkspaceId by remember {
            mutableStateOf(registryPreferences.get("activeWorkspaceId", null) ?: workspaces.firstOrNull()?.id)
        }
        val activeWorkspace = workspaces.firstOrNull { it.id == activeWorkspaceId } ?: workspaces.firstOrNull()
        val rootPath = activeWorkspace?.path
        val format = activeWorkspace?.format
        val root = rootPath?.let(::File)
        if (root == null || !root.isDirectory) {
            NotesDirectorySetupScreen(onChooseNotesDirectory = {
                chooseDirectory(selectDirectoryTitle)?.let { directory ->
                    val added = DesktopWorkspace(UUID.randomUUID().toString(), directory.name, directory.absolutePath, null)
                    workspaces = workspaces + added
                    saveDesktopWorkspaces(registryPreferences, workspaces, added.id)
                    activeWorkspaceId = added.id
                }
            })
            return@DesktopMacOsTheme
        }
        if (format == null) {
            FormatSetupScreen(onFormatSelected = { selectedFormat ->
                workspaces = workspaces.map {
                    if (it.id == activeWorkspace!!.id) it.copy(format = selectedFormat) else it
                }
                saveDesktopWorkspaces(registryPreferences, workspaces, activeWorkspace!!.id)
            })
            return@DesktopMacOsTheme
        }

        val preferences = remember(activeWorkspace!!.id) { registryPreferences.node("workspace_${activeWorkspace.id}") }
        val storage = remember(rootPath) { DesktopNotesDirectoryStorage(root) }
        val taskRepository = remember(storage, format) { NotesDirectoryTaskRepository(storage, checkNotNull(format)) }
        var quickNotesFolder by remember { mutableStateOf(preferences.get("quickNotesFolder", NotesDirectoryQuickNoteRepository.QUICK_NOTES_FOLDER)) }
        val quickNoteRepository = remember(storage, quickNotesFolder) {
            NotesDirectoryQuickNoteRepository(storage, quickNotesFolder)
        }
        val syncStore = remember(activeWorkspace.id) { SyncStatusStore() }
        val syncMutex = remember(activeWorkspace.id) { Mutex() }
        var syncConfig by remember(storage) { mutableStateOf<TresseConfig?>(null) }

        fun syncEngine(config: TresseConfig) = TresseSync(fileTresseVault(root) { ignoredPath(it, config) })
        suspend fun refresh() {
            taskRepository.refresh()
            quickNoteRepository.refresh()
        }
        suspend fun perform(forcePull: Boolean): String = syncMutex.withLock {
            val config = checkNotNull(syncConfig) { syncNotConfiguredError }
            syncStore.begin(syncTriggerDesktop)
            return try {
                val engine = syncEngine(config)
                val result = if (forcePull) engine.forcePull(config, syncStore.progress())
                else engine.perform(config, syncStore.progress())
                refresh()
                val summary = if (result.pushed == 0 && result.pulled == 0) {
                    getString(Res.string.sync_already_up_to_date)
                } else {
                    getString(Res.string.sync_result_summary, result.pushed, result.pulled)
                }
                summary.also {
                    syncStore.finishOk(it, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
                }
            } catch (error: Throwable) {
                syncStore.finishError(error.message ?: syncErrorGeneric)
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

        LaunchedEffect(workspaces) {
            val lastRuns = mutableMapOf<String, Long>()
            while (true) {
                delay(60_000)
                val now = System.currentTimeMillis()
                workspaces.forEach { workspace ->
                    val directory = File(workspace.path)
                    if (!directory.isDirectory) return@forEach
                    val workspacePreferences = registryPreferences.node("workspace_${workspace.id}")
                    val engine = TresseSync(fileTresseVault(directory))
                    val config = engine.loadConfig()?.withLocalSync(workspacePreferences) ?: return@forEach
                    if (!config.autoSyncEnabled || !config.isComplete()) return@forEach
                    val interval = config.autoSyncIntervalMinutes.coerceAtLeast(1) * 60_000
                    if (now - (lastRuns[workspace.id] ?: 0L) < interval) return@forEach
                    lastRuns[workspace.id] = now
                    runCatching {
                        TresseSync(fileTresseVault(directory) { ignoredPath(it, config) }).perform(config)
                    }.onSuccess {
                        if (workspace.id == activeWorkspace.id) refresh()
                    }
                }
            }
        }

        ShardkeepApp(
            repository = taskRepository,
            quickNoteRepository = quickNoteRepository,
            today = currentLocalDate(),
            workspaceName = activeWorkspace.name,
            workspaces = workspaces.map { WorkspaceOption(it.id, it.name) },
            onWorkspaceSelected = { id ->
                registryPreferences.put("activeWorkspaceId", id)
                activeWorkspaceId = id
            },
            onAddWorkspace = {
                chooseDirectory(selectDirectoryTitle)?.let { directory ->
                    val added = DesktopWorkspace(UUID.randomUUID().toString(), directory.name, directory.absolutePath, null)
                    workspaces = workspaces + added
                    saveDesktopWorkspaces(registryPreferences, workspaces, added.id)
                    activeWorkspaceId = added.id
                }
            },
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

private data class DesktopWorkspace(
    val id: String,
    val name: String,
    val path: String,
    val format: TaskFormat?,
)

private fun loadDesktopWorkspaces(
    preferences: Preferences,
    defaultWorkspaceName: String,
): List<DesktopWorkspace> {
    var ids = preferences.get("workspaceIds", "").split(',').filter(String::isNotBlank)
    if (ids.isEmpty()) {
        val legacyPath = preferences.get("notesDirectory", null)
        if (legacyPath != null) {
            val id = UUID.randomUUID().toString()
            val node = preferences.node("workspace_$id")
            node.put("name", File(legacyPath).name.ifBlank { "Shardkeep" })
            node.put("path", legacyPath)
            preferences.get("taskFormat", null)?.let { node.put("format", it) }
            preferences.get("quickNotesFolder", null)?.let { node.put("quickNotesFolder", it) }
            ids = listOf(id)
            preferences.put("workspaceIds", id)
            preferences.put("activeWorkspaceId", id)
        }
    }
    return ids.mapNotNull { id ->
        val node = preferences.node("workspace_$id")
        val path = node.get("path", null) ?: return@mapNotNull null
        DesktopWorkspace(
            id = id,
            name = node.get("name", File(path).name.ifBlank { defaultWorkspaceName }),
            path = path,
            format = node.get("format", null)?.let { runCatching { TaskFormat.valueOf(it) }.getOrNull() },
        )
    }
}

private fun saveDesktopWorkspaces(
    preferences: Preferences,
    workspaces: List<DesktopWorkspace>,
    activeId: String,
) {
    preferences.put("workspaceIds", workspaces.joinToString(",") { it.id })
    preferences.put("activeWorkspaceId", activeId)
    workspaces.forEach { workspace ->
        preferences.node("workspace_${workspace.id}").apply {
            put("name", workspace.name)
            put("path", workspace.path)
            workspace.format?.let { put("format", it.name) }
        }
    }
}

private fun chooseDirectory(title: String): File? = JFileChooser().apply {
    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    dialogTitle = title
}.let { chooser ->
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
