package com.akergez.shardkeep.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.NotesDirectoryLocation
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.repository.TaskDestination
import com.akergez.shardkeep.domain.repository.TaskDestinationProvider
import com.akergez.shardkeep.domain.model.TaskFilter
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.repository.QuickNoteRepository
import com.akergez.shardkeep.domain.repository.TaskRepository
import com.akergez.shardkeep.domain.usecase.DayTaskSections
import com.akergez.shardkeep.domain.usecase.TaskLocations
import com.akergez.shardkeep.domain.usecase.buildTaskFiles
import com.akergez.shardkeep.domain.usecase.buildTaskLocations
import com.akergez.shardkeep.domain.usecase.filterTasks
import com.akergez.shardkeep.domain.usecase.groupDayTasks
import com.akergez.shardkeep.ui.notes.NoteEditorTarget
import com.akergez.shardkeep.ui.task.TaskDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Состояние оболочки приложения.
 *
 * Репозитории и сегодняшняя дата приходят снаружи через [bind] и могут смениться
 * (переключение воркспейса, наступившая полночь), поэтому живут во флоу: подписки
 * переезжают на новый источник сами, без пересоздания вьюмодели.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShardkeepViewModel : ViewModel() {

    private data class Bindings(
        val repository: TaskRepository? = null,
        val quickNotes: QuickNoteRepository? = null,
        val today: CalendarDate? = null,
    )

    private data class ShellState(
        val refreshing: Boolean = false,
        val focusedTaskFilePath: String? = null,
    )

    private val bindings = MutableStateFlow(Bindings())
    private val section = MutableStateFlow(AppSection.Tasks)
    private val filter = MutableStateFlow(TaskFilter())
    private val pending = MutableStateFlow(PendingEdits())
    private val shell = MutableStateFlow(ShellState())

    private val _dialogs = MutableStateFlow(ShardkeepDialogs())
    val dialogs: StateFlow<ShardkeepDialogs> = _dialogs.asStateFlow()

    private val _noteEditorTarget = MutableStateFlow<NoteEditorTarget?>(null)
    val noteEditorTarget: StateFlow<NoteEditorTarget?> = _noteEditorTarget.asStateFlow()

    /** Одноразовые сообщения снекбару: их показывают, а не хранят. */
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val allTasks: StateFlow<List<Task>> = bindings
        .map { it.repository }
        .distinctUntilChanged()
        .flatMapLatest { it?.observeTasks() ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    /** Вкладки «Всех задач» дороги: за списком файлов надо идти в хранилище, поэтому только в своём разделе. */
    private val taskFiles: StateFlow<List<TaskDestination>> =
        combine(bindings, section, allTasks) { binding, currentSection, tasks ->
            Triple(binding.repository, currentSection, tasks)
        }.mapLatest { (repository, currentSection, tasks) ->
            if (currentSection != AppSection.AllTasks) {
                emptyList()
            } else {
                val destinations = runCatching {
                    (repository as? TaskDestinationProvider)?.listDestinations().orEmpty()
                }.getOrDefault(emptyList())
                buildTaskFiles(tasks, destinations)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    private data class TaskContent(
        val filter: TaskFilter,
        val dayTasks: DayTaskSections,
        val visibleTaskCount: Int,
        val locations: TaskLocations,
        val tasksByFile: Map<String, List<Task>>,
    )

    private val content: Flow<TaskContent> =
        combine(bindings, allTasks, filter, pending) { binding, tasks, currentFilter, edits ->
            val today = binding.today
            val visible = if (today == null) emptyList() else filterTasks(tasks, currentFilter, today)
            // Раскладываем по секциям и файлам до наложения правок: иначе отмеченная
            // задача прыгнула бы в «сделано» ещё до того, как её приняли на диске.
            val grouped = if (today == null) {
                DayTaskSections()
            } else {
                groupDayTasks(visible, today, currentFilter.range)
            }
            TaskContent(
                filter = currentFilter,
                dayTasks = grouped.map(edits::apply),
                visibleTaskCount = visible.size,
                locations = buildTaskLocations(tasks),
                tasksByFile = tasks.asSequence()
                    .filter { it.status == TaskStatus.Todo }
                    .groupBy { it.location.path }
                    .mapValues { (_, fileTasks) -> fileTasks.map(edits::apply) },
            )
        }

    val uiState: StateFlow<ShardkeepUiState> =
        combine(content, section, shell, taskFiles) { taskContent, currentSection, shellState, files ->
            ShardkeepUiState(
                section = currentSection,
                filter = taskContent.filter,
                refreshing = shellState.refreshing,
                dayTasks = taskContent.dayTasks,
                visibleTaskCount = taskContent.visibleTaskCount,
                taskLocations = taskContent.locations,
                taskFiles = files,
                focusedTaskFilePath = shellState.focusedTaskFilePath,
                tasksByFile = taskContent.tasksByFile,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), ShardkeepUiState())

    fun bind(repository: TaskRepository, quickNotes: QuickNoteRepository?, today: CalendarDate) {
        bindings.value = Bindings(repository, quickNotes, today)
    }

    // --- Навигация и фильтры ---

    fun selectSection(newSection: AppSection) {
        section.value = newSection
    }

    fun setFilter(newFilter: TaskFilter) {
        filter.value = newFilter
    }

    fun setFocusedTaskFile(path: String?) {
        shell.update { it.copy(focusedTaskFilePath = path) }
    }

    /**
     * Обновление вручную. Синхронизацию передаёт вызывающий: её колбэки живут
     * в платформенной обвязке и пересоздаются на каждой рекомпозиции — захватывать
     * их во вьюмодели значит однажды дёрнуть протухший.
     */
    fun refresh(sync: (suspend () -> Unit)? = null) {
        if (shell.value.refreshing) return
        val binding = bindings.value
        val repository = binding.repository ?: return
        shell.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            runCatching {
                sync?.invoke()
                repository.refresh()
                binding.quickNotes?.refresh()
            }
                .onSuccess { post(UiMessage.RefreshComplete) }
                .onFailure { post(UiMessage.RefreshFailed) }
            shell.update { it.copy(refreshing = false) }
        }
    }

    // --- Задачи ---

    fun setTaskStatus(taskId: TaskId, checked: Boolean) {
        val repository = bindings.value.repository ?: return
        val newStatus = if (checked) TaskStatus.Done else TaskStatus.Todo
        pending.update { it.copy(statuses = it.statuses + (taskId to newStatus)) }
        viewModelScope.launch {
            runCatching { repository.setStatus(taskId, newStatus) }
                .onFailure { post(UiMessage.TaskSaveFailed) }
            pending.update { it.copy(statuses = it.statuses - taskId) }
        }
    }

    fun scheduleToday(task: Task) {
        val binding = bindings.value
        val repository = binding.repository ?: return
        val today = binding.today ?: return
        pending.update { it.copy(scheduledDates = it.scheduledDates + (task.id to today)) }
        viewModelScope.launch {
            runCatching { repository.setScheduledDate(task.id, today) }
                .onFailure { post(UiMessage.ScheduleTodayFailed) }
            pending.update { it.copy(scheduledDates = it.scheduledDates - task.id) }
        }
    }

    // --- Диалог задачи ---

    fun openCreateTask(pathOverride: String? = null) {
        _dialogs.update { it.copy(showCreateTask = true, createTaskPathOverride = pathOverride) }
    }

    fun editTask(task: Task) {
        _dialogs.update { it.copy(editingTask = task) }
    }

    fun dismissTaskDialog() {
        _dialogs.update {
            it.copy(showCreateTask = false, createTaskPathOverride = null, editingTask = null)
        }
    }

    fun saveTask(draft: TaskDraft) {
        val repository = bindings.value.repository ?: return
        val edited = _dialogs.value.editingTask
        val ordinal = uiState.value.visibleTaskCount
        viewModelScope.launch {
            runCatching {
                if (edited == null) {
                    repository.create(draft.toNewTask(ordinal))
                } else {
                    repository.update(draft.applyTo(edited))
                }
            }.onSuccess {
                _dialogs.update {
                    it.copy(
                        showCreateTask = false,
                        createTaskPathOverride = null,
                        editingTask = null,
                        lastUsedPath = draft.path,
                        lastUsedHeading = draft.heading.orEmpty(),
                    )
                }
            }.onFailure {
                post(UiMessage.TaskSaveFailed)
            }
        }
    }

    // --- Заметки ---

    fun openNoteTypePicker() {
        _dialogs.update { it.copy(showNoteTypePicker = true) }
    }

    fun dismissNoteTypePicker() {
        _dialogs.update { it.copy(showNoteTypePicker = false) }
    }

    fun openNoteEditor(target: NoteEditorTarget) {
        _dialogs.update { it.copy(showNoteTypePicker = false) }
        _noteEditorTarget.value = target
    }

    fun closeNoteEditor() {
        _noteEditorTarget.value = null
    }

    fun requestDeleteNote(note: QuickNote) {
        _dialogs.update { it.copy(noteToDelete = note) }
    }

    fun dismissDeleteNote() {
        _dialogs.update { it.copy(noteToDelete = null) }
    }

    fun confirmDeleteNote() {
        val note = _dialogs.value.noteToDelete ?: return
        val repository = bindings.value.quickNotes
        _dialogs.update { it.copy(noteToDelete = null) }
        viewModelScope.launch {
            runCatching { repository?.delete(note.id) }
                .onFailure { post(UiMessage.NoteDeleteFailed) }
        }
    }

    // --- Диплинки ---

    /**
     * Переход по нажатию на виджет. Заметки на холодном старте ещё не прочитаны с
     * диска, поэтому нужную ждём — но недолго, иначе экран замрёт на пустом месте.
     */
    suspend fun handleDeepLink(deepLink: AppDeepLink) {
        when (deepLink) {
            AppDeepLink.Tasks -> section.value = AppSection.Tasks
            AppDeepLink.Notes -> {
                _noteEditorTarget.value = null
                section.value = AppSection.Notes
            }
            is AppDeepLink.OpenNote -> {
                val note = bindings.value.quickNotes?.let { repository ->
                    withTimeoutOrNull(NOTE_WAIT_TIMEOUT_MS) {
                        repository.observeNotes()
                            .first { notes -> notes.any { it.id.path == deepLink.path } }
                    }?.firstOrNull { it.id.path == deepLink.path }
                }
                if (note != null) {
                    _noteEditorTarget.value = NoteEditorTarget.Edit(note)
                } else {
                    section.value = AppSection.Notes
                }
            }
        }
    }

    fun post(message: UiMessage) {
        _messages.trySend(message)
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        const val NOTE_WAIT_TIMEOUT_MS = 3_000L
    }
}

private fun TaskDraft.toNewTask(ordinal: Int): Task = Task(
    id = TaskId("task-$dueDate-$title-$ordinal"),
    title = title,
    status = TaskStatus.Todo,
    dueDate = dueDate,
    scheduledDate = scheduledDate,
    startDate = startDate,
    endDate = endDate,
    time = time,
    recurrence = recurrence,
    priority = priority,
    location = NotesDirectoryLocation(path, heading),
)

/** Правка не переносит задачу между файлами: путь и заголовок задаются при создании. */
private fun TaskDraft.applyTo(task: Task): Task = task.copy(
    title = title,
    dueDate = dueDate,
    scheduledDate = scheduledDate,
    startDate = startDate,
    endDate = endDate,
    time = time,
    recurrence = recurrence,
    priority = priority,
)
