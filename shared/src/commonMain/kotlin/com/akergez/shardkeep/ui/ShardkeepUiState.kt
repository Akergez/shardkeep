package com.akergez.shardkeep.ui

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.repository.TaskDestination
import com.akergez.shardkeep.domain.model.TaskFilter
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.usecase.DayTaskSections
import com.akergez.shardkeep.domain.usecase.TaskLocations

/**
 * Правки, отправленные в репозиторий, но ещё не вернувшиеся из него.
 *
 * Галочка должна отзываться мгновенно, а запись в markdown идёт через диск и разбор
 * файла. До ответа показываем задачу такой, какой её только что сделал пользователь.
 */
data class PendingEdits(
    val statuses: Map<TaskId, TaskStatus> = emptyMap(),
    val scheduledDates: Map<TaskId, CalendarDate> = emptyMap(),
) {
    fun apply(task: Task): Task {
        val status = statuses[task.id]
        val scheduledDate = scheduledDates[task.id]
        if (status == null && scheduledDate == null) return task
        return task.copy(
            status = status ?: task.status,
            scheduledDate = scheduledDate ?: task.scheduledDate,
        )
    }
}

/**
 * Всё, что нужно оболочке для отрисовки, и ничего больше: задачи уже отфильтрованы,
 * разложены по секциям дня и по файлам, а оптимистичные правки в них учтены.
 */
data class ShardkeepUiState(
    val section: AppSection = AppSection.Tasks,
    val filter: TaskFilter = TaskFilter(),
    val refreshing: Boolean = false,
    /** Лента дня: просрочено / активно / сделано. */
    val dayTasks: DayTaskSections = DayTaskSections(),
    /** Сколько задач попало в текущий фильтр — по нему решается, показывать ли заглушку. */
    val visibleTaskCount: Int = 0,
    /** Файлы и папки, между которыми переключается фильтр. */
    val taskLocations: TaskLocations = TaskLocations(),
    /** Вкладки раздела «Все задачи» — по одной на файл с невыполненными задачами. */
    val taskFiles: List<TaskDestination> = emptyList(),
    val focusedTaskFilePath: String? = null,
    /** Невыполненные задачи, разложенные по файлам, для страниц раздела «Все задачи». */
    val tasksByFile: Map<String, List<Task>> = emptyMap(),
)

/** Диалоги оболочки. Разложены отдельно от контента: они меняются от нажатий, а не от данных. */
data class ShardkeepDialogs(
    val showCreateTask: Boolean = false,
    /** Файл, в который создаётся задача из раздела «Все задачи»; `null` — обычное создание. */
    val createTaskPathOverride: String? = null,
    val editingTask: Task? = null,
    val showNoteTypePicker: Boolean = false,
    val noteToDelete: QuickNote? = null,
    /** Куда по умолчанию кладём новую задачу — туда же, куда положили прошлую. */
    val lastUsedPath: String = DEFAULT_TASK_PATH,
    val lastUsedHeading: String = "",
) {
    val isTaskDialogVisible: Boolean get() = showCreateTask || editingTask != null
}

const val DEFAULT_TASK_PATH: String = "Shardkeep/Inbox.md"
