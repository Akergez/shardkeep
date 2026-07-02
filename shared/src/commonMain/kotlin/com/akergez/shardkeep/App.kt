package com.akergez.shardkeep

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.data.InMemoryTaskRepository
import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.ChecklistItem
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskFilter
import com.akergez.shardkeep.domain.model.TaskFormat
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskPriority
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.model.TimeRange
import com.akergez.shardkeep.domain.model.NotesDirectoryLocation
import com.akergez.shardkeep.domain.repository.QuickNoteRepository
import com.akergez.shardkeep.domain.repository.TaskRepository
import com.akergez.shardkeep.domain.repository.TaskDestination
import com.akergez.shardkeep.domain.repository.TaskDestinationProvider
import com.akergez.shardkeep.domain.usecase.ObserveFilteredTasks
import com.akergez.shardkeep.platform.currentLocalDate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.ic_add
import shardkeep.shared.generated.resources.ic_arrow_back
import shardkeep.shared.generated.resources.ic_article
import shardkeep.shared.generated.resources.ic_delete
import shardkeep.shared.generated.resources.ic_calendar_today
import shardkeep.shared.generated.resources.ic_event
import shardkeep.shared.generated.resources.ic_event_available
import shardkeep.shared.generated.resources.ic_format_list
import shardkeep.shared.generated.resources.ic_schedule
import shardkeep.shared.generated.resources.all_tasks
import shardkeep.shared.generated.resources.app_name
import shardkeep.shared.generated.resources.cancel
import shardkeep.shared.generated.resources.choose_another_folder
import shardkeep.shared.generated.resources.choose_folder
import shardkeep.shared.generated.resources.connect_notes_directory
import shardkeep.shared.generated.resources.connect_notes_directory_description
import shardkeep.shared.generated.resources.create
import shardkeep.shared.generated.resources.default_tasks_heading
import shardkeep.shared.generated.resources.demo_check_ui
import shardkeep.shared.generated.resources.demo_connect_notes_directory
import shardkeep.shared.generated.resources.indexing_notes_directory
import shardkeep.shared.generated.resources.new_task
import shardkeep.shared.generated.resources.no_tasks
import shardkeep.shared.generated.resources.no_tasks_hint
import shardkeep.shared.generated.resources.retry
import shardkeep.shared.generated.resources.task_title_label
import shardkeep.shared.generated.resources.today
import shardkeep.shared.generated.resources.notes_directory_open_error
import shardkeep.shared.generated.resources.notes_directory_open_error_description
import shardkeep.shared.generated.resources.week
import shardkeep.shared.generated.resources.choose_existing_location
import shardkeep.shared.generated.resources.due_date
import shardkeep.shared.generated.resources.file_path
import shardkeep.shared.generated.resources.heading_optional
import shardkeep.shared.generated.resources.invalid_date
import shardkeep.shared.generated.resources.refresh
import shardkeep.shared.generated.resources.refresh_complete
import shardkeep.shared.generated.resources.refresh_error
import shardkeep.shared.generated.resources.add_item
import shardkeep.shared.generated.resources.note_update_error
import shardkeep.shared.generated.resources.save
import shardkeep.shared.generated.resources.schedule_today
import shardkeep.shared.generated.resources.schedule_today_error
import shardkeep.shared.generated.resources.task_save_error
import shardkeep.shared.generated.resources.all_locations
import shardkeep.shared.generated.resources.file_filter
import shardkeep.shared.generated.resources.folder_filter
import shardkeep.shared.generated.resources.location_filter
import shardkeep.shared.generated.resources.location_filter_value
import shardkeep.shared.generated.resources.checklist
import shardkeep.shared.generated.resources.new_note
import shardkeep.shared.generated.resources.no_notes
import shardkeep.shared.generated.resources.no_notes_hint
import shardkeep.shared.generated.resources.note_content
import shardkeep.shared.generated.resources.note_delete_confirm
import shardkeep.shared.generated.resources.note_delete_error
import shardkeep.shared.generated.resources.note_save_error
import shardkeep.shared.generated.resources.note_title
import shardkeep.shared.generated.resources.done_tasks
import shardkeep.shared.generated.resources.overdue_tasks
import shardkeep.shared.generated.resources.delete
import shardkeep.shared.generated.resources.priority
import shardkeep.shared.generated.resources.priority_high
import shardkeep.shared.generated.resources.priority_low
import shardkeep.shared.generated.resources.priority_normal
import shardkeep.shared.generated.resources.quick_notes
import shardkeep.shared.generated.resources.tasks
import shardkeep.shared.generated.resources.text_note
import shardkeep.shared.generated.resources.format_continue
import shardkeep.shared.generated.resources.format_dataview
import shardkeep.shared.generated.resources.format_dataview_description
import shardkeep.shared.generated.resources.format_emoji
import shardkeep.shared.generated.resources.format_emoji_description
import shardkeep.shared.generated.resources.format_select_description
import shardkeep.shared.generated.resources.format_select_title
import shardkeep.shared.generated.resources.ic_settings
import shardkeep.shared.generated.resources.settings
import shardkeep.shared.generated.resources.quick_notes_folder
import shardkeep.shared.generated.resources.quick_notes_folder_hint
import shardkeep.shared.generated.resources.notifications
import shardkeep.shared.generated.resources.notifications_description
import shardkeep.shared.generated.resources.settings_saved
import shardkeep.shared.generated.resources.no_settings_available
import shardkeep.shared.generated.resources.sync_title
import shardkeep.shared.generated.resources.sync_config_hint
import shardkeep.shared.generated.resources.repo_id
import shardkeep.shared.generated.resources.encryption_key
import shardkeep.shared.generated.resources.s3_endpoint
import shardkeep.shared.generated.resources.s3_region
import shardkeep.shared.generated.resources.s3_bucket
import shardkeep.shared.generated.resources.s3_access_key_id
import shardkeep.shared.generated.resources.s3_secret_access_key
import shardkeep.shared.generated.resources.server_url
import shardkeep.shared.generated.resources.access_token
import shardkeep.shared.generated.resources.auto_sync
import shardkeep.shared.generated.resources.sync_interval_minutes
import shardkeep.shared.generated.resources.sync_debounce_seconds
import shardkeep.shared.generated.resources.ignore_patterns
import shardkeep.shared.generated.resources.import_config
import shardkeep.shared.generated.resources.config_import_error
import shardkeep.shared.generated.resources.action_import
import shardkeep.shared.generated.resources.sync_phase_idle
import shardkeep.shared.generated.resources.sync_phase_preparing
import shardkeep.shared.generated.resources.sync_phase_committing
import shardkeep.shared.generated.resources.sync_phase_transferring
import shardkeep.shared.generated.resources.sync_phase_applying
import shardkeep.shared.generated.resources.sync_phase_done
import shardkeep.shared.generated.resources.sync_phase_error
import shardkeep.shared.generated.resources.sync_read
import shardkeep.shared.generated.resources.sync_written
import shardkeep.shared.generated.resources.sync_configured
import shardkeep.shared.generated.resources.sync_not_configured
import shardkeep.shared.generated.resources.sync_auto_indicator
import shardkeep.shared.generated.resources.syncing
import shardkeep.shared.generated.resources.sync_now

@Composable
@Preview
fun App() {
    MaterialTheme {
        val today = remember { currentLocalDate() }
        val demoConnectNotesDirectory = stringResource(Res.string.demo_connect_notes_directory)
        val demoCheckUi = stringResource(Res.string.demo_check_ui)
        val defaultHeading = stringResource(Res.string.default_tasks_heading)
        val repository = remember(today, demoConnectNotesDirectory, demoCheckUi, defaultHeading) {
            demoRepository(today, demoConnectNotesDirectory, demoCheckUi, defaultHeading)
        }
        ShardkeepApp(repository = repository, today = today)
    }
}

/**
 * Editable view of the Tresse sync config. The `.tresse/config.toml` file in the notes directory
 * is the single source of truth — this draft is read from it and written straight back to it; there is
 * no separate internal store of these settings.
 */
data class SyncConfigDraft(
    val serverUrl: String = "",
    val repoId: String = "",
    val token: String = "",
    val encryptionKey: String = "",
    val storageType: String = "http",
    val s3Endpoint: String = "",
    val s3Region: String = "",
    val s3Bucket: String = "",
    val s3AccessKeyId: String = "",
    val s3SecretAccessKey: String = "",
    val autoSyncEnabled: Boolean = false,
    val autoSyncIntervalMinutes: Long = 15,
    val autoSyncDebounceSeconds: Long = 10,
    val ignorePatterns: List<String> = listOf(".git", ".trash", ".obsidian"),
)

data class SettingsCallbacks(
    val quickNotesFolder: String,
    val onFolderChange: (String) -> Unit,
    val notificationsEnabled: Boolean,
    val onNotificationsEnabledChange: (Boolean) -> Unit,
    val syncConfig: SyncConfigDraft? = null,
    val onSyncConfigChange: suspend (SyncConfigDraft) -> Unit = { },
    val onImportSyncConfig: suspend (String) -> Boolean = { false },
    /** Runs a sync and returns a short human-readable summary; throws on failure. */
    val onSync: (suspend () -> String)? = null,
    /** Live sync status (manual + background); when null, the status panel is hidden. */
    val syncState: StateFlow<SyncState>? = null,
)

private enum class AppSection { Tasks, AllTasks, Notes, Settings }

private sealed interface NoteEditorTarget {
    data object NewText : NoteEditorTarget
    data object NewChecklist : NoteEditorTarget
    data class Edit(val note: QuickNote) : NoteEditorTarget
}

/** Navigation intent produced by a home-screen widget tap. */
sealed interface AppDeepLink {
    data object Tasks : AppDeepLink
    data object Notes : AppDeepLink
    data class OpenNote(val path: String) : AppDeepLink
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShardkeepApp(
    repository: TaskRepository,
    today: CalendarDate,
    quickNoteRepository: QuickNoteRepository? = null,
    settingsCallbacks: SettingsCallbacks? = null,
    deepLink: AppDeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    var filter by remember { mutableStateOf(TaskFilter()) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var noteEditorTarget by remember { mutableStateOf<NoteEditorTarget?>(null) }
    var showNoteTypePicker by remember { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(AppSection.Tasks) }
    val defaultTasksHeading = stringResource(Res.string.default_tasks_heading)
    var lastUsedPath by rememberSaveable { mutableStateOf("Shardkeep/Inbox.md") }
    var lastUsedHeading by rememberSaveable { mutableStateOf("") }
    val taskFlow = remember(repository, filter, today) {
        ObserveFilteredTasks(repository)(filter, today)
    }
    val allTasks by remember(repository) { repository.observeTasks() }.collectAsState(emptyList())
    val tasks by taskFlow.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }
    var pendingStatusOverrides by remember { mutableStateOf(mapOf<TaskId, TaskStatus>()) }
    var pendingScheduledDateOverrides by remember { mutableStateOf(mapOf<TaskId, CalendarDate>()) }
    var noteToDelete by remember { mutableStateOf<QuickNote?>(null) }
    val refreshCompleteMessage = stringResource(Res.string.refresh_complete)
    val refreshErrorMessage = stringResource(Res.string.refresh_error)
    val taskSaveErrorMessage = stringResource(Res.string.task_save_error)
    val scheduleTodayErrorMessage = stringResource(Res.string.schedule_today_error)
    val noteSaveErrorMessage = stringResource(Res.string.note_save_error)
    val noteDeleteErrorMessage = stringResource(Res.string.note_delete_error)
    val noteUpdateErrorMessage = stringResource(Res.string.note_update_error)

    LaunchedEffect(deepLink) {
        when (val target = deepLink) {
            null -> return@LaunchedEffect
            AppDeepLink.Tasks -> section = AppSection.Tasks
            AppDeepLink.Notes -> {
                noteEditorTarget = null
                section = AppSection.Notes
            }
            is AppDeepLink.OpenNote -> {
                // The note may not be loaded yet on a cold launch; wait briefly for it.
                val note = quickNoteRepository?.let { repo ->
                    withTimeoutOrNull(3000) {
                        repo.observeNotes().first { notes -> notes.any { it.id.path == target.path } }
                    }?.firstOrNull { it.id.path == target.path }
                }
                if (note != null) {
                    noteEditorTarget = NoteEditorTarget.Edit(note)
                } else {
                    section = AppSection.Notes
                }
            }
        }
        onDeepLinkHandled()
    }

    val currentEditorTarget = noteEditorTarget
    if (currentEditorTarget != null && quickNoteRepository != null) {
        NoteEditorScreen(
            target = currentEditorTarget,
            repository = quickNoteRepository,
            onBack = { noteEditorTarget = null },
            onError = { scope.launch { snackbarHostState.showSnackbar(noteUpdateErrorMessage) } },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                actions = {
                    TextButton(
                        enabled = !refreshing,
                        onClick = {
                            scope.launch {
                                refreshing = true
                                runCatching {
                                    repository.refresh()
                                    quickNoteRepository?.refresh()
                                }
                                    .onSuccess { snackbarHostState.showSnackbar(refreshCompleteMessage) }
                                    .onFailure { snackbarHostState.showSnackbar(refreshErrorMessage) }
                                refreshing = false
                            }
                        },
                    ) { Text(stringResource(Res.string.refresh)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            when (section) {
                AppSection.Tasks -> FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_add),
                        contentDescription = stringResource(Res.string.new_task),
                    )
                }
                AppSection.Notes -> if (quickNoteRepository != null) {
                    FloatingActionButton(onClick = { showNoteTypePicker = true }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_add),
                            contentDescription = stringResource(Res.string.new_note),
                        )
                    }
                }
                AppSection.AllTasks, AppSection.Settings -> {}
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = section == AppSection.Tasks,
                    onClick = { section = AppSection.Tasks },
                    icon = { Icon(painter = painterResource(Res.drawable.ic_calendar_today), contentDescription = null) },
                    label = { Text(stringResource(Res.string.tasks)) },
                )
                NavigationBarItem(
                    selected = section == AppSection.AllTasks,
                    onClick = { section = AppSection.AllTasks },
                    icon = { Icon(painter = painterResource(Res.drawable.ic_format_list), contentDescription = null) },
                    label = { Text(stringResource(Res.string.all_tasks)) },
                )
                if (quickNoteRepository != null) {
                    NavigationBarItem(
                        selected = section == AppSection.Notes,
                        onClick = { section = AppSection.Notes },
                        icon = { Icon(painter = painterResource(Res.drawable.ic_article), contentDescription = null) },
                        label = { Text(stringResource(Res.string.quick_notes)) },
                    )
                }
                NavigationBarItem(
                    selected = section == AppSection.Settings,
                    onClick = { section = AppSection.Settings },
                    icon = { Icon(painter = painterResource(Res.drawable.ic_settings), contentDescription = null) },
                    label = { Text(stringResource(Res.string.settings)) },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (section) {
                AppSection.Tasks -> Column(modifier = Modifier.fillMaxSize()) {
                    TimeRangeSelector(
                        selected = filter.range,
                        onSelect = { filter = filter.copy(range = it) },
                    )
                    LocationFilter(
                        tasks = allTasks,
                        filter = filter,
                        onChange = { filter = it },
                    )
                    val overdueTasks = remember(tasks, today, filter.range) {
                        if (filter.range == TimeRange.Day) {
                            tasks.filter { task ->
                                (task.scheduledDate ?: task.dueDate)?.let { it < today } == true
                            }
                        } else {
                            emptyList()
                        }
                    }
                    val overdueIds = remember(overdueTasks) { overdueTasks.mapTo(HashSet()) { it.id } }
                    val doneTasks = remember(tasks) { tasks.filter { it.status == TaskStatus.Done } }
                    val overdueTasksLabel = stringResource(Res.string.overdue_tasks)
                    val doneTasksLabel = stringResource(Res.string.done_tasks)
                    if (tasks.isEmpty()) {
                        EmptyTasks(modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                            if (overdueTasks.isNotEmpty()) {
                                item(key = "header_overdue") {
                                    Text(
                                        text = overdueTasksLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    )
                                }
                                items(overdueTasks, key = { it.id.value }) { task ->
                                    val effectiveStatus = pendingStatusOverrides[task.id] ?: task.status
                                    TaskRow(
                                        task = task.copy(status = effectiveStatus),
                                        onCheckedChange = { checked ->
                                            handleStatusChange(task.id, checked, scope, repository, taskSaveErrorMessage, snackbarHostState,
                                                onAddPending = { id, status -> pendingStatusOverrides = pendingStatusOverrides + (id to status) },
                                                onRemovePending = { id -> pendingStatusOverrides = pendingStatusOverrides - id },
                                            )
                                        },
                                    )
                                    HorizontalDivider()
                                }
                            }
                            items(
                                tasks.filter { it.id !in overdueIds && it.status != TaskStatus.Done },
                                key = { it.id.value },
                            ) { task ->
                                val effectiveStatus = pendingStatusOverrides[task.id] ?: task.status
                                TaskRow(
                                    task = task.copy(status = effectiveStatus),
                                    onCheckedChange = { checked ->
                                        handleStatusChange(task.id, checked, scope, repository, taskSaveErrorMessage, snackbarHostState,
                                            onAddPending = { id, status -> pendingStatusOverrides = pendingStatusOverrides + (id to status) },
                                            onRemovePending = { id -> pendingStatusOverrides = pendingStatusOverrides - id },
                                        )
                                    },
                                )
                                HorizontalDivider()
                            }
                            if (doneTasks.isNotEmpty()) {
                                item(key = "header_done") {
                                    Text(
                                        text = doneTasksLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    )
                                }
                                items(doneTasks, key = { it.id.value }) { task ->
                                    val effectiveStatus = pendingStatusOverrides[task.id] ?: task.status
                                    TaskRow(
                                        task = task.copy(status = effectiveStatus),
                                        onCheckedChange = { checked ->
                                            handleStatusChange(task.id, checked, scope, repository, taskSaveErrorMessage, snackbarHostState,
                                                onAddPending = { id, status -> pendingStatusOverrides = pendingStatusOverrides + (id to status) },
                                                onRemovePending = { id -> pendingStatusOverrides = pendingStatusOverrides - id },
                                            )
                                        },
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                AppSection.AllTasks -> {
                    val scheduleTodayLabel = stringResource(Res.string.schedule_today)
                    AllTasksContent(
                        tasks = allTasks.filter { it.status == TaskStatus.Todo },
                        today = today,
                        pendingStatusOverrides = pendingStatusOverrides,
                        pendingScheduledDateOverrides = pendingScheduledDateOverrides,
                        scheduleTodayLabel = scheduleTodayLabel,
                        onCheckedChange = { task, checked ->
                            handleStatusChange(task.id, checked, scope, repository, taskSaveErrorMessage, snackbarHostState,
                                onAddPending = { id, status -> pendingStatusOverrides = pendingStatusOverrides + (id to status) },
                                onRemovePending = { id -> pendingStatusOverrides = pendingStatusOverrides - id },
                            )
                        },
                        onScheduleToday = { task ->
                            pendingScheduledDateOverrides = pendingScheduledDateOverrides + (task.id to today)
                            scope.launch {
                                runCatching { repository.setScheduledDate(task.id, today) }
                                    .onSuccess { pendingScheduledDateOverrides = pendingScheduledDateOverrides - task.id }
                                    .onFailure {
                                        pendingScheduledDateOverrides = pendingScheduledDateOverrides - task.id
                                        snackbarHostState.showSnackbar(scheduleTodayErrorMessage)
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                AppSection.Notes -> if (quickNoteRepository != null) {
                    QuickNotesContent(
                        repository = quickNoteRepository,
                        onError = { scope.launch { snackbarHostState.showSnackbar(noteSaveErrorMessage) } },
                        onRequestDelete = { noteToDelete = it },
                        onNoteClick = { note -> noteEditorTarget = NoteEditorTarget.Edit(note) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                AppSection.Settings -> {
                    val settingsSavedMessage = stringResource(Res.string.settings_saved)
                    SettingsContent(
                        callbacks = settingsCallbacks,
                        onSaved = { scope.launch { snackbarHostState.showSnackbar(settingsSavedMessage) } },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTaskDialog(
            destinationProvider = repository as? TaskDestinationProvider,
            defaultPath = lastUsedPath,
            defaultHeading = lastUsedHeading.ifEmpty { defaultTasksHeading },
            defaultDate = today,
            onDismiss = { showCreateDialog = false },
            onCreate = { draft ->
                scope.launch {
                    runCatching {
                        repository.create(
                            Task(
                                id = TaskId("task-${draft.dueDate}-${draft.title}-${tasks.size}"),
                                title = draft.title,
                                status = TaskStatus.Todo,
                                dueDate = draft.dueDate,
                                priority = draft.priority,
                                location = NotesDirectoryLocation(
                                    draft.path,
                                    draft.heading,
                                ),
                            ),
                        )
                    }.onSuccess {
                        lastUsedPath = draft.path
                        lastUsedHeading = draft.heading.orEmpty()
                        showCreateDialog = false
                    }.onFailure {
                        snackbarHostState.showSnackbar(taskSaveErrorMessage)
                    }
                }
            },
        )
    }
    if (showNoteTypePicker) {
        AlertDialog(
            onDismissRequest = { showNoteTypePicker = false },
            title = { Text(stringResource(Res.string.new_note)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showNoteTypePicker = false; noteEditorTarget = NoteEditorTarget.NewText },
                    ) { Text(stringResource(Res.string.text_note)) }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showNoteTypePicker = false; noteEditorTarget = NoteEditorTarget.NewChecklist },
                    ) { Text(stringResource(Res.string.checklist)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showNoteTypePicker = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text(stringResource(Res.string.note_delete_confirm)) },
            text = { Text(note.title) },
            confirmButton = {
                TextButton(onClick = {
                    val captured = note
                    noteToDelete = null
                    scope.launch {
                        runCatching {
                            quickNoteRepository?.delete(captured.id)
                        }.onFailure {
                            snackbarHostState.showSnackbar(noteDeleteErrorMessage)
                        }
                    }
                }) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

private fun handleStatusChange(
    taskId: TaskId,
    checked: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    repository: TaskRepository,
    errorMessage: String,
    snackbarHostState: SnackbarHostState,
    onAddPending: (TaskId, TaskStatus) -> Unit,
    onRemovePending: (TaskId) -> Unit,
) {
    val newStatus = if (checked) TaskStatus.Done else TaskStatus.Todo
    onAddPending(taskId, newStatus)
    scope.launch {
        runCatching { repository.setStatus(taskId, newStatus) }
            .onSuccess { onRemovePending(taskId) }
            .onFailure {
                onRemovePending(taskId)
                snackbarHostState.showSnackbar(errorMessage)
            }
    }
}

@Composable
private fun AllTasksContent(
    tasks: List<Task>,
    today: CalendarDate,
    pendingStatusOverrides: Map<TaskId, TaskStatus>,
    pendingScheduledDateOverrides: Map<TaskId, CalendarDate>,
    scheduleTodayLabel: String,
    onCheckedChange: (Task, Boolean) -> Unit,
    onScheduleToday: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) {
        EmptyTasks(modifier = modifier)
        return
    }
    val grouped = remember(tasks) {
        tasks.groupBy { it.location.path }.entries.sortedBy { it.key }
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
        grouped.forEach { (path, fileTasks) ->
            item(key = "header_$path") {
                Text(
                    text = path.substringAfterLast('/').removeSuffix(".md"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            items(fileTasks, key = { it.id.value }) { task ->
                val effectiveTask = task.copy(
                    status = pendingStatusOverrides[task.id] ?: task.status,
                    scheduledDate = pendingScheduledDateOverrides[task.id] ?: task.scheduledDate,
                )
                AllTaskRow(
                    task = effectiveTask,
                    today = today,
                    scheduleTodayLabel = scheduleTodayLabel,
                    onCheckedChange = { checked -> onCheckedChange(task, checked) },
                    onScheduleToday = { onScheduleToday(task) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AllTaskRow(
    task: Task,
    today: CalendarDate,
    scheduleTodayLabel: String,
    onCheckedChange: (Boolean) -> Unit,
    onScheduleToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleCheckbox(
            checked = task.status == TaskStatus.Done,
            onCheckedChange = onCheckedChange,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.status == TaskStatus.Done) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
            )
            TaskAttributes(task = task, showLocation = false)
        }
        if (task.scheduledDate != today && task.status != TaskStatus.Done) {
            FilledTonalIconButton(onClick = onScheduleToday) {
                Icon(
                    painter = painterResource(Res.drawable.ic_event_available),
                    contentDescription = scheduleTodayLabel,
                )
            }
        }
    }
}

@Composable
private fun LocationFilter(
    tasks: List<Task>,
    filter: TaskFilter,
    onChange: (TaskFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val files = remember(tasks) { tasks.map { it.location.path }.distinct().sorted() }
    val folders = remember(files) {
        files.mapNotNull { path ->
            path.substringBeforeLast('/', missingDelimiterValue = "").ifEmpty { null }
        }.distinct().sorted()
    }
    val label = when {
        filter.file != null -> stringResource(Res.string.file_filter, filter.file)
        filter.folder != null -> stringResource(Res.string.folder_filter, filter.folder)
        else -> stringResource(Res.string.all_locations)
    }

    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        TextButton(onClick = { expanded = true }) {
            Text(
                stringResource(
                    Res.string.location_filter_value,
                    stringResource(Res.string.location_filter),
                    label,
                ),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.all_locations)) },
                onClick = {
                    onChange(filter.copy(folder = null, file = null))
                    expanded = false
                },
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.folder_filter, folder)) },
                    onClick = {
                        onChange(filter.copy(folder = folder, file = null))
                        expanded = false
                    },
                )
            }
            files.forEach { file ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.file_filter, file)) },
                    onClick = {
                        onChange(filter.copy(folder = null, file = file))
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun NotesDirectorySetupScreen(onChooseNotesDirectory: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.connect_notes_directory), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(Res.string.connect_notes_directory_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onChooseNotesDirectory) { Text(stringResource(Res.string.choose_folder)) }
        }
    }
}

@Composable
fun FormatSetupScreen(onFormatSelected: (TaskFormat) -> Unit) {
    var selected by remember { mutableStateOf(TaskFormat.Emoji) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.format_select_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(Res.string.format_select_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FormatOption(
                    name = stringResource(Res.string.format_emoji),
                    description = stringResource(Res.string.format_emoji_description),
                    selected = selected == TaskFormat.Emoji,
                    onClick = { selected = TaskFormat.Emoji },
                )
                FormatOption(
                    name = stringResource(Res.string.format_dataview),
                    description = stringResource(Res.string.format_dataview_description),
                    selected = selected == TaskFormat.Dataview,
                    onClick = { selected = TaskFormat.Dataview },
                )
            }
            Button(onClick = { onFormatSelected(selected) }) {
                Text(stringResource(Res.string.format_continue))
            }
        }
    }
}

@Composable
private fun FormatOption(
    name: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
fun NotesDirectoryLoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.indexing_notes_directory))
    }
}

@Composable
fun NotesDirectoryErrorScreen(onRetry: () -> Unit, onChooseAnother: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.notes_directory_open_error), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(Res.string.notes_directory_open_error_description),
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
            TextButton(onClick = onChooseAnother) {
                Text(stringResource(Res.string.choose_another_folder))
            }
        }
    }
}

@Composable
private fun QuickNotesContent(
    repository: QuickNoteRepository,
    onError: () -> Unit,
    onRequestDelete: (QuickNote) -> Unit,
    onNoteClick: (QuickNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notes by remember(repository) { repository.observeNotes() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    if (notes.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.no_notes), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.no_notes_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
        items(notes, key = { it.id.path }) { note ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNoteClick(note) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRequestDelete(note) }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_delete),
                            contentDescription = stringResource(Res.string.delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when (note) {
                    is QuickNote.Text -> Text(
                        note.content,
                        maxLines = 3,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is QuickNote.Checklist -> Column {
                        note.items.take(4).forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = item.isChecked,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            runCatching {
                                                repository.setChecklistItem(note.id, item.index, checked)
                                            }.onFailure { onError() }
                                        }
                                    },
                                )
                                Text(
                                    item.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (note.items.size > 4) {
                            Text(
                                "+ ${note.items.size - 4}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SettingsContent(
    callbacks: SettingsCallbacks?,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (callbacks == null) {
            Text(
                text = stringResource(Res.string.settings),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(Res.string.no_settings_available),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            text = stringResource(Res.string.settings),
            style = MaterialTheme.typography.headlineSmall,
        )

        var folderDraft by rememberSaveable(callbacks.quickNotesFolder) {
            mutableStateOf(callbacks.quickNotesFolder)
        }
        val folderChanged = folderDraft.trim() != callbacks.quickNotesFolder

        OutlinedTextField(
            value = folderDraft,
            onValueChange = { folderDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.quick_notes_folder)) },
            placeholder = { Text(stringResource(Res.string.quick_notes_folder_hint)) },
            singleLine = true,
        )
        if (folderChanged) {
            Button(
                onClick = {
                    callbacks.onFolderChange(folderDraft.trim())
                    onSaved()
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(Res.string.save))
            }
        }

        HorizontalDivider()

        Text(stringResource(Res.string.sync_title), style = MaterialTheme.typography.titleMedium)
        var syncing by remember { mutableStateOf(false) }
        callbacks.syncState?.let { state ->
            SyncStatusCard(
                stateFlow = state,
                syncing = syncing,
                onSyncNow = callbacks.onSync?.let { sync ->
                    {
                        scope.launch {
                            syncing = true
                            runCatching { sync() }
                            syncing = false
                        }
                    }
                },
            )
        }
        Text(
            stringResource(Res.string.sync_config_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val current = callbacks.syncConfig ?: SyncConfigDraft()
        var storageType by rememberSaveable(current) { mutableStateOf(current.storageType) }
        var server by rememberSaveable(current) { mutableStateOf(current.serverUrl) }
        var repo by rememberSaveable(current) { mutableStateOf(current.repoId) }
        var token by rememberSaveable(current) { mutableStateOf(current.token) }
        var key by rememberSaveable(current) { mutableStateOf(current.encryptionKey) }
        var s3Endpoint by rememberSaveable(current) { mutableStateOf(current.s3Endpoint) }
        var s3Region by rememberSaveable(current) { mutableStateOf(current.s3Region) }
        var s3Bucket by rememberSaveable(current) { mutableStateOf(current.s3Bucket) }
        var s3AccessKeyId by rememberSaveable(current) { mutableStateOf(current.s3AccessKeyId) }
        var s3SecretAccessKey by rememberSaveable(current) { mutableStateOf(current.s3SecretAccessKey) }
        var autoSync by rememberSaveable(current) { mutableStateOf(current.autoSyncEnabled) }
        var interval by rememberSaveable(current) { mutableStateOf(current.autoSyncIntervalMinutes.toString()) }
        var debounce by rememberSaveable(current) { mutableStateOf(current.autoSyncDebounceSeconds.toString()) }
        var ignore by rememberSaveable(current) { mutableStateOf(current.ignorePatterns.joinToString("\n")) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = storageType != "s3",
                onClick = { storageType = "http" },
                label = { Text("HTTP") },
            )
            FilterChip(
                selected = storageType == "s3",
                onClick = { storageType = "s3" },
                label = { Text("S3") },
            )
        }
        OutlinedTextField(repo, { repo = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.repo_id)) }, singleLine = true)
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.encryption_key)) }, singleLine = true)
        if (storageType == "s3") {
            OutlinedTextField(s3Endpoint, { s3Endpoint = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.s3_endpoint)) }, placeholder = { Text("https://storage.yandexcloud.net") }, singleLine = true)
            OutlinedTextField(s3Region, { s3Region = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.s3_region)) }, placeholder = { Text("ru-central1") }, singleLine = true)
            OutlinedTextField(s3Bucket, { s3Bucket = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.s3_bucket)) }, singleLine = true)
            OutlinedTextField(s3AccessKeyId, { s3AccessKeyId = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.s3_access_key_id)) }, singleLine = true)
            OutlinedTextField(s3SecretAccessKey, { s3SecretAccessKey = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.s3_secret_access_key)) }, singleLine = true)
        } else {
            OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.server_url)) }, singleLine = true)
            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.access_token)) }, singleLine = true)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(Res.string.auto_sync), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = autoSync, onCheckedChange = { autoSync = it })
        }
        OutlinedTextField(
            interval,
            { interval = it.filter(Char::isDigit) },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.sync_interval_minutes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            debounce,
            { debounce = it.filter(Char::isDigit) },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.sync_debounce_seconds)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            ignore,
            { ignore = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.ignore_patterns)) },
            minLines = 2,
        )
        val buildDraft = {
            SyncConfigDraft(
                serverUrl = server.trim(),
                repoId = repo.trim(),
                token = token.trim(),
                encryptionKey = key.trim(),
                storageType = if (storageType == "s3") "s3" else "http",
                s3Endpoint = s3Endpoint.trim(),
                s3Region = s3Region.trim(),
                s3Bucket = s3Bucket.trim(),
                s3AccessKeyId = s3AccessKeyId.trim(),
                s3SecretAccessKey = s3SecretAccessKey.trim(),
                autoSyncEnabled = autoSync,
                autoSyncIntervalMinutes = interval.toLongOrNull() ?: current.autoSyncIntervalMinutes,
                autoSyncDebounceSeconds = debounce.toLongOrNull() ?: current.autoSyncDebounceSeconds,
                ignorePatterns = ignore.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
            )
        }

        var configString by rememberSaveable { mutableStateOf("") }
        var configImportFailed by rememberSaveable { mutableStateOf(false) }
        OutlinedTextField(
            value = configString,
            onValueChange = { configString = it; configImportFailed = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.import_config)) },
            placeholder = { Text("[remote]\nurl = \"https://…\"") },
            minLines = 5,
        )
        if (configImportFailed) {
            Text(stringResource(Res.string.config_import_error), color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                scope.launch {
                    val imported = callbacks.onImportSyncConfig(configString)
                    configImportFailed = !imported
                    if (imported) {
                        configString = ""
                        onSaved()
                    }
                }
            }) { Text(stringResource(Res.string.action_import)) }
            TextButton(onClick = {
                scope.launch {
                    callbacks.onSyncConfigChange(buildDraft())
                    onSaved()
                }
            }) { Text(stringResource(Res.string.save)) }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.notifications),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(Res.string.notifications_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = callbacks.notificationsEnabled,
                onCheckedChange = callbacks.onNotificationsEnabledChange,
            )
        }
    }
}

@Composable
private fun phaseLabelOf(phase: SyncPhase): String = when (phase) {
    SyncPhase.Idle -> stringResource(Res.string.sync_phase_idle)
    SyncPhase.Preparing -> stringResource(Res.string.sync_phase_preparing)
    SyncPhase.Committing -> stringResource(Res.string.sync_phase_committing)
    SyncPhase.Transferring -> stringResource(Res.string.sync_phase_transferring)
    SyncPhase.Applying -> stringResource(Res.string.sync_phase_applying)
    SyncPhase.Done -> stringResource(Res.string.sync_phase_done)
    SyncPhase.Error -> stringResource(Res.string.sync_phase_error)
}

@Composable
private fun progressLineOf(state: SyncState): String? {
    val readLabel = stringResource(Res.string.sync_read, state.committed)
    val writtenLabel = stringResource(Res.string.sync_written, state.applied)
    return buildList {
        if (state.committed > 0) add(readLabel)
        if (state.uploaded > 0 || state.downloaded > 0) add("↑${state.uploaded} ↓${state.downloaded}")
        if (state.applied > 0) add(writtenLabel)
    }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Prominent, self-contained sync hub — status, live stage + per-object progress,
 * storage state, last result, and the action button all in one card. Mirrors the
 * companion plugin's activity view; driven by the host's [SyncStatusStore], so
 * manual and background syncs render identically.
 */
@Composable
private fun SyncStatusCard(
    stateFlow: StateFlow<SyncState>,
    syncing: Boolean,
    onSyncNow: (() -> Unit)?,
) {
    val state by stateFlow.collectAsState()
    val accent = when (state.phase) {
        SyncPhase.Error -> MaterialTheme.colorScheme.error
        SyncPhase.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Status headline + trigger tag.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.active) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = phaseLabelOf(state.phase),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            state.trigger?.takeIf { state.active }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Live progress: indeterminate bar + per-stage counters while active.
        if (state.active) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        progressLineOf(state)?.takeIf { state.active }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Result / error of the last finished sync.
        if (!state.active) {
            val message = state.error ?: state.lastResult
            if (message != null) {
                Text(
                    text = state.lastSyncLabel?.takeIf { state.error == null }
                        ?.let { "$message · $it" } ?: message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Storage status line.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (state.storageType == "s3") "S3" else "HTTP",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("·", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (state.configured) stringResource(Res.string.sync_configured) else stringResource(Res.string.sync_not_configured),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.configured) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
            )
            if (state.autoSyncEnabled) {
                Text(stringResource(Res.string.sync_auto_indicator), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Action.
        onSyncNow?.let { run ->
            Button(
                onClick = run,
                enabled = !syncing && !state.active,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (syncing || state.active) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (syncing || state.active) stringResource(Res.string.syncing) else stringResource(Res.string.sync_now))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorScreen(
    target: NoteEditorTarget,
    repository: QuickNoteRepository,
    onBack: () -> Unit,
    onError: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    val isChecklist = target == NoteEditorTarget.NewChecklist ||
        (target is NoteEditorTarget.Edit && target.note is QuickNote.Checklist)

    var title by remember {
        mutableStateOf(if (target is NoteEditorTarget.Edit) target.note.title else "")
    }
    var content by remember {
        mutableStateOf(if (target is NoteEditorTarget.Edit && target.note is QuickNote.Text) target.note.content else "")
    }
    val items = remember {
        mutableStateListOf<ChecklistItem>().also { list ->
            if (target is NoteEditorTarget.Edit && target.note is QuickNote.Checklist) {
                list.addAll(target.note.items)
            }
        }
    }

    val canSave = !saving && title.isNotBlank() &&
        if (isChecklist) items.any { it.text.isNotBlank() } else content.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = null)
                    }
                },
                title = {
                    Text(if (target is NoteEditorTarget.Edit) target.note.title else stringResource(Res.string.new_note))
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            saving = true
                            scope.launch {
                                runCatching {
                                    when (target) {
                                        NoteEditorTarget.NewText ->
                                            repository.createTextNote(title.trim(), content.trim())
                                        NoteEditorTarget.NewChecklist ->
                                            repository.createChecklist(
                                                title.trim(),
                                                items.map { it.text.trim() }.filter { it.isNotBlank() },
                                            )
                                        is NoteEditorTarget.Edit -> when (val note = target.note) {
                                            is QuickNote.Text ->
                                                repository.updateTextNote(note.id, title.trim(), content.trim())
                                            is QuickNote.Checklist ->
                                                repository.updateChecklist(note.id, title.trim(), items.filter { it.text.isNotBlank() })
                                        }
                                    }
                                }.onSuccess { onBack() }
                                .onFailure { onError(); saving = false }
                            }
                        },
                    ) { Text(stringResource(Res.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.note_title)) },
                singleLine = true,
            )

            if (!isChecklist) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.note_content)) },
                    minLines = 8,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                items.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleCheckbox(
                            checked = item.isChecked,
                            onCheckedChange = { checked -> items[index] = item.copy(isChecked = checked) },
                        )
                        TextField(
                            value = item.text,
                            onValueChange = { text -> items[index] = item.copy(text = text) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        )
                        IconButton(onClick = { items.removeAt(index) }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_delete),
                                contentDescription = stringResource(Res.string.delete),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
                }
                TextButton(
                    onClick = { items.add(ChecklistItem(index = items.size, text = "", isChecked = false)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.add_item))
                }
            }
        }
    }
}

@Composable
private fun TimeRangeSelector(
    selected: TimeRange,
    onSelect: (TimeRange) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == TimeRange.Day,
            onClick = { onSelect(TimeRange.Day) },
            label = { Text(stringResource(Res.string.today)) },
        )
        FilterChip(
            selected = selected == TimeRange.Week,
            onClick = { onSelect(TimeRange.Week) },
            label = { Text(stringResource(Res.string.week)) },
        )
    }
}

@Composable
private fun TaskChip(
    painter: Painter,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
private fun TaskAttributes(
    task: Task,
    showLocation: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val hasContent = task.scheduledDate != null || task.dueDate != null || showLocation
    if (!hasContent) return

    Row(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        task.scheduledDate?.let { date ->
            TaskChip(
                painter = painterResource(Res.drawable.ic_schedule),
                label = date.toString(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        task.dueDate?.let { date ->
            TaskChip(
                painter = painterResource(Res.drawable.ic_event),
                label = date.toString(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (showLocation) {
            TaskChip(
                painter = painterResource(Res.drawable.ic_article),
                label = task.location.path.substringAfterLast('/').removeSuffix(".md"),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CircleCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val fill by animateColorAsState(if (checked) primary else Color.Transparent)
    val stroke by animateColorAsState(if (checked) primary else outline)
    val checkmark by animateColorAsState(if (checked) Color.White else Color.Transparent)

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val r = size.minDimension / 2f
            drawCircle(color = fill, radius = r)
            drawCircle(color = stroke, radius = r, style = Stroke(width = 2.dp.toPx()))
            val path = Path().apply {
                moveTo(size.width * 0.20f, size.height * 0.50f)
                lineTo(size.width * 0.43f, size.height * 0.72f)
                lineTo(size.width * 0.80f, size.height * 0.28f)
            }
            drawPath(
                path = path,
                color = checkmark,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CircleCheckbox(
            checked = task.status == TaskStatus.Done,
            onCheckedChange = onCheckedChange,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.status == TaskStatus.Done) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
            )
            TaskAttributes(task = task, showLocation = true)
        }
    }
}

@Composable
private fun EmptyTasks(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.no_tasks), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.no_tasks_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreateTaskDialog(
    destinationProvider: TaskDestinationProvider?,
    defaultPath: String,
    defaultHeading: String,
    defaultDate: CalendarDate,
    onDismiss: () -> Unit,
    onCreate: (TaskDraft) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var path by rememberSaveable { mutableStateOf(defaultPath) }
    var heading by rememberSaveable { mutableStateOf(defaultHeading) }
    var dueDateText by rememberSaveable { mutableStateOf(defaultDate.toString()) }
    var priority by rememberSaveable { mutableStateOf(TaskPriority.Normal) }
    var destinations by remember { mutableStateOf<List<TaskDestination>>(emptyList()) }
    var destinationMenuExpanded by remember { mutableStateOf(false) }
    val parsedDate = remember(dueDateText) { parseCalendarDate(dueDateText) }

    LaunchedEffect(destinationProvider) {
        destinations = runCatching { destinationProvider?.listDestinations().orEmpty() }
            .getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.new_task)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.task_title_label)) },
                    singleLine = true,
                )
                if (destinations.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { destinationMenuExpanded = true }) {
                            Text(stringResource(Res.string.choose_existing_location))
                        }
                        DropdownMenu(
                            expanded = destinationMenuExpanded,
                            onDismissRequest = { destinationMenuExpanded = false },
                        ) {
                            destinations.forEach { destination ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            listOfNotNull(destination.path, destination.heading)
                                                .joinToString(" › "),
                                        )
                                    },
                                    onClick = {
                                        path = destination.path
                                        heading = destination.heading.orEmpty()
                                        destinationMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.file_path)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = heading,
                    onValueChange = { heading = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.heading_optional)) },
                    singleLine = true,
                )
                Text(
                    stringResource(Res.string.priority),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = priority == TaskPriority.Low,
                        onClick = { priority = TaskPriority.Low },
                        label = { Text(stringResource(Res.string.priority_low)) },
                    )
                    FilterChip(
                        selected = priority == TaskPriority.Normal,
                        onClick = { priority = TaskPriority.Normal },
                        label = { Text(stringResource(Res.string.priority_normal)) },
                    )
                    FilterChip(
                        selected = priority == TaskPriority.High,
                        onClick = { priority = TaskPriority.High },
                        label = { Text(stringResource(Res.string.priority_high)) },
                    )
                }
                OutlinedTextField(
                    value = dueDateText,
                    onValueChange = { dueDateText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.due_date)) },
                    supportingText = {
                        if (dueDateText.isNotBlank() && parsedDate == null) {
                            Text(stringResource(Res.string.invalid_date))
                        }
                    },
                    isError = dueDateText.isNotBlank() && parsedDate == null,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && path.isNotBlank() && parsedDate != null,
                onClick = {
                    onCreate(
                        TaskDraft(
                            title = title.trim(),
                            path = normalizeMarkdownPath(path),
                            heading = heading.trim().ifEmpty { null },
                            dueDate = checkNotNull(parsedDate),
                            priority = priority,
                        ),
                    )
                },
            ) { Text(stringResource(Res.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

private data class TaskDraft(
    val title: String,
    val path: String,
    val heading: String?,
    val dueDate: CalendarDate,
    val priority: TaskPriority = TaskPriority.Normal,
)

private fun parseCalendarDate(value: String): CalendarDate? {
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(value.trim()) ?: return null
    return runCatching {
        CalendarDate(
            year = match.groupValues[1].toInt(),
            month = match.groupValues[2].toInt(),
            day = match.groupValues[3].toInt(),
        )
    }.getOrNull()
}

private fun normalizeMarkdownPath(value: String): String {
    val trimmed = value.trim().trimStart('/')
    return if (trimmed.endsWith(".md", ignoreCase = true)) trimmed else "$trimmed.md"
}

private fun demoRepository(
    today: CalendarDate,
    connectNotesDirectoryTitle: String,
    checkUiTitle: String,
    defaultHeading: String,
): TaskRepository = InMemoryTaskRepository(
    listOf(
        Task(
            id = TaskId("demo-1"),
            title = connectNotesDirectoryTitle,
            status = TaskStatus.Todo,
            dueDate = today,
            location = NotesDirectoryLocation("Shardkeep/Inbox.md", defaultHeading),
        ),
        Task(
            id = TaskId("demo-2"),
            title = checkUiTitle,
            status = TaskStatus.Done,
            dueDate = today,
            location = NotesDirectoryLocation("Shardkeep/Inbox.md", defaultHeading),
        ),
    ),
)
