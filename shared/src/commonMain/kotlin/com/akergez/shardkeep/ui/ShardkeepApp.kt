package com.akergez.shardkeep.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.repository.TaskDestinationProvider
import com.akergez.shardkeep.domain.repository.QuickNoteRepository
import com.akergez.shardkeep.domain.repository.TaskRepository
import com.akergez.shardkeep.platform.hasFinePointer
import com.akergez.shardkeep.ui.alltasks.AllTasksScreen
import com.akergez.shardkeep.ui.navigation.AppNavigationBar
import com.akergez.shardkeep.ui.navigation.AppNavigationLayout
import com.akergez.shardkeep.ui.navigation.AppNavigationRail
import com.akergez.shardkeep.ui.navigation.AppSidebar
import com.akergez.shardkeep.ui.navigation.navigationLayoutFor
import com.akergez.shardkeep.ui.notes.NoteEditorScreen
import com.akergez.shardkeep.ui.notes.NoteEditorTarget
import com.akergez.shardkeep.ui.notes.QuickNotesScreen
import com.akergez.shardkeep.ui.notes.QuickNotesViewModel
import com.akergez.shardkeep.ui.settings.SettingsCallbacks
import com.akergez.shardkeep.ui.settings.SettingsScreen
import com.akergez.shardkeep.ui.task.CreateTaskDialog
import com.akergez.shardkeep.ui.tasks.TodayTasksScreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.add_workspace
import shardkeep.shared.generated.resources.cancel
import shardkeep.shared.generated.resources.checklist
import shardkeep.shared.generated.resources.default_tasks_heading
import shardkeep.shared.generated.resources.delete
import shardkeep.shared.generated.resources.ic_add
import shardkeep.shared.generated.resources.ic_refresh
import shardkeep.shared.generated.resources.new_note
import shardkeep.shared.generated.resources.new_task
import shardkeep.shared.generated.resources.note_delete_confirm
import shardkeep.shared.generated.resources.refresh
import shardkeep.shared.generated.resources.text_note

/**
 * Оболочка приложения: навигация, шапка, кнопки действий и диалоги.
 *
 * Данные и решения о них живут в [ShardkeepViewModel] — здесь только вёрстка и
 * платформенные колбэки, которые хост передаёт снаружи.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShardkeepApp(
    repository: TaskRepository,
    today: CalendarDate,
    quickNoteRepository: QuickNoteRepository? = null,
    settingsCallbacks: SettingsCallbacks? = null,
    deepLink: AppDeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
    workspaceName: String = "Shardkeep",
    workspaces: List<WorkspaceOption> = emptyList(),
    onWorkspaceSelected: (String) -> Unit = {},
    onAddWorkspace: () -> Unit = {},
    viewModel: ShardkeepViewModel = viewModel { ShardkeepViewModel() },
    quickNotesViewModel: QuickNotesViewModel = viewModel { QuickNotesViewModel() },
) {
    LaunchedEffect(repository, quickNoteRepository, today) {
        viewModel.bind(repository, quickNoteRepository, today)
    }
    LaunchedEffect(quickNoteRepository) {
        quickNoteRepository?.let(quickNotesViewModel::bind)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
    val noteEditorTarget by viewModel.noteEditorTarget.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageTexts = rememberUiMessageTexts()

    LaunchedEffect(messageTexts) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(messageTexts.getValue(message))
        }
    }
    LaunchedEffect(deepLink) {
        val target = deepLink ?: return@LaunchedEffect
        viewModel.handleDeepLink(target)
        onDeepLinkHandled()
    }

    // Редактор заметки занимает экран целиком: у оболочки под ним не остаётся работы.
    val editorTarget = noteEditorTarget
    if (editorTarget != null && quickNoteRepository != null) {
        NoteEditorScreen(
            target = editorTarget,
            repository = quickNoteRepository,
            onBack = viewModel::closeNoteEditor,
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Width alone decides, as M3 specifies. Keying this off the aspect ratio, as it once
        // was, asks the wrong question: a tall narrow desktop window is landscape-negative yet
        // has no room for a rail, and a phone held sideways is landscape yet has no room either.
        val navigation = navigationLayoutFor(maxWidth, hasFinePointer())
        Row(modifier = Modifier.fillMaxSize()) {
            when (navigation) {
                AppNavigationLayout.Sidebar -> AppSidebar(
                    section = state.section,
                    showNotes = quickNoteRepository != null,
                    onSectionChange = viewModel::selectSection,
                )
                AppNavigationLayout.Rail -> AppNavigationRail(
                    section = state.section,
                    showNotes = quickNoteRepository != null,
                    onSectionChange = viewModel::selectSection,
                )
                AppNavigationLayout.Bar -> Unit
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                // Scaffold defaults to `background`, which the native palette maps to the
                // colour behind a window rather than under a list. Elsewhere the two roles
                // agree; on macOS `background` is Aqua's grey window fill and `surface` the
                // white a list is supposed to sit on.
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    TopAppBar(
                        title = {
                            WorkspaceTitle(
                                workspaceName = workspaceName,
                                workspaces = workspaces,
                                onWorkspaceSelected = onWorkspaceSelected,
                                onAddWorkspace = onAddWorkspace,
                            )
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    ShardkeepActions(
                        section = state.section,
                        refreshing = state.refreshing,
                        focusedTaskFilePath = state.focusedTaskFilePath,
                        showNotes = quickNoteRepository != null,
                        onRefresh = { viewModel.refresh(sync = settingsCallbacks.syncAction()) },
                        onCreateTask = viewModel::openCreateTask,
                        onNewNote = viewModel::openNoteTypePicker,
                    )
                },
                bottomBar = {
                    if (navigation == AppNavigationLayout.Bar) {
                        AppNavigationBar(
                            section = state.section,
                            showNotes = quickNoteRepository != null,
                            onSectionChange = viewModel::selectSection,
                        )
                    }
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    when (state.section) {
                        AppSection.Tasks -> TodayTasksScreen(
                            dayTasks = state.dayTasks,
                            filter = state.filter,
                            locations = state.taskLocations,
                            isEmpty = state.visibleTaskCount == 0,
                            onFilterChange = viewModel::setFilter,
                            onTaskCheckedChange = viewModel::setTaskStatus,
                            onEditTask = viewModel::editTask,
                            modifier = Modifier.fillMaxSize(),
                        )

                        AppSection.AllTasks -> AllTasksScreen(
                            files = state.taskFiles,
                            tasksByFile = state.tasksByFile,
                            focusedFilePath = state.focusedTaskFilePath,
                            today = today,
                            onCheckedChange = viewModel::setTaskStatus,
                            onScheduleToday = viewModel::scheduleToday,
                            onEdit = viewModel::editTask,
                            onFocusedFileChange = viewModel::setFocusedTaskFile,
                            modifier = Modifier.fillMaxSize(),
                        )

                        AppSection.Notes -> if (quickNoteRepository != null) {
                            QuickNotesScreen(
                                viewModel = quickNotesViewModel,
                                onError = { viewModel.post(UiMessage.NoteSaveFailed) },
                                onRequestDelete = viewModel::requestDeleteNote,
                                onNoteClick = { note ->
                                    viewModel.openNoteEditor(NoteEditorTarget.Edit(note))
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        AppSection.Settings -> SettingsScreen(
                            callbacks = settingsCallbacks,
                            onSaved = { viewModel.post(UiMessage.SettingsSaved) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    if (dialogs.isTaskDialogVisible) {
        val edited = dialogs.editingTask
        val defaultTasksHeading = stringResource(Res.string.default_tasks_heading)
        CreateTaskDialog(
            destinationProvider = repository as? TaskDestinationProvider,
            defaultPath = edited?.location?.path ?: dialogs.createTaskPathOverride ?: dialogs.lastUsedPath,
            defaultHeading = edited?.location?.heading.orEmpty().ifEmpty {
                // Задача, созданная из конкретного файла, не наследует заголовок прошлой.
                if (dialogs.createTaskPathOverride != null) {
                    ""
                } else {
                    dialogs.lastUsedHeading.ifEmpty { defaultTasksHeading }
                }
            },
            fixedPath = edited?.location?.path ?: dialogs.createTaskPathOverride,
            defaultDate = edited?.dueDate ?: today,
            initialTask = edited,
            onDismiss = viewModel::dismissTaskDialog,
            onCreate = viewModel::saveTask,
        )
    }
    if (dialogs.showNoteTypePicker) {
        NoteTypePickerDialog(
            onDismiss = viewModel::dismissNoteTypePicker,
            onSelect = viewModel::openNoteEditor,
        )
    }
    dialogs.noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteNote,
            title = { Text(stringResource(Res.string.note_delete_confirm)) },
            text = { Text(note.title) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteNote) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteNote) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

/**
 * Синхронизация перед обновлением — только если она вообще настроена. Её итоговую
 * сводку здесь не показывают: об успехе и провале сообщает снекбар обновления.
 */
private fun SettingsCallbacks?.syncAction(): (suspend () -> Unit)? =
    this?.takeIf { it.syncConfigured }?.onSync?.let { sync -> { sync() } }

/** Имя воркспейса в шапке — оно же переключатель между ними. */
@Composable
private fun WorkspaceTitle(
    workspaceName: String,
    workspaces: List<WorkspaceOption>,
    onWorkspaceSelected: (String) -> Unit,
    onAddWorkspace: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(text = workspaceName, style = MaterialTheme.typography.titleLarge)
            Text(text = "  ▾", style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            workspaces.forEach { workspace ->
                DropdownMenuItem(
                    text = { Text(workspace.name) },
                    onClick = {
                        expanded = false
                        onWorkspaceSelected(workspace.id)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.add_workspace)) },
                onClick = {
                    expanded = false
                    onAddWorkspace()
                },
            )
        }
    }
}

/** Обновление доступно везде, «плюс» — только там, где есть что создавать. */
@Composable
private fun ShardkeepActions(
    section: AppSection,
    refreshing: Boolean,
    focusedTaskFilePath: String?,
    showNotes: Boolean,
    onRefresh: () -> Unit,
    onCreateTask: (String?) -> Unit,
    onNewNote: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallFloatingActionButton(onClick = onRefresh) {
            if (refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    painter = painterResource(Res.drawable.ic_refresh),
                    contentDescription = stringResource(Res.string.refresh),
                )
            }
        }

        when (section) {
            AppSection.Tasks -> CreateTaskButton { onCreateTask(null) }
            AppSection.AllTasks -> focusedTaskFilePath?.let { path ->
                CreateTaskButton { onCreateTask(path) }
            }
            AppSection.Notes -> if (showNotes) {
                FloatingActionButton(onClick = onNewNote) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_add),
                        contentDescription = stringResource(Res.string.new_note),
                    )
                }
            }
            AppSection.Settings -> Unit
        }
    }
}

@Composable
private fun CreateTaskButton(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Icon(
            painter = painterResource(Res.drawable.ic_add),
            contentDescription = stringResource(Res.string.new_task),
        )
    }
}

@Composable
private fun NoteTypePickerDialog(onDismiss: () -> Unit, onSelect: (NoteEditorTarget) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.new_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelect(NoteEditorTarget.NewText) },
                ) { Text(stringResource(Res.string.text_note)) }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelect(NoteEditorTarget.NewChecklist) },
                ) { Text(stringResource(Res.string.checklist)) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
