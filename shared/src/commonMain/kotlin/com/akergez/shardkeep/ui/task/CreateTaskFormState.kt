package com.akergez.shardkeep.ui.task

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskPriority
import com.akergez.shardkeep.domain.model.TaskTimeSpan
import com.akergez.shardkeep.domain.model.calendarDateFromEpochDay
import com.akergez.shardkeep.domain.model.toEpochDay

/** Какое из полей-дат сейчас выбирают календарём; `null` — календарь закрыт. */
enum class TaskDateField { Due, Scheduled, Start, End }

/**
 * Состояние формы задачи: поля, их разбор и готовность к сохранению.
 *
 * Держится отдельно от диалога, чтобы отрисовка занималась только версткой. Здесь же
 * живут правила формы — какое поле когда имеет смысл и что считать ошибкой ввода.
 */
@Stable
class CreateTaskFormState(
    defaultPath: String,
    defaultHeading: String,
    private val defaultDate: CalendarDate,
    val initialTask: Task?,
) {
    var title by mutableStateOf(initialTask?.title.orEmpty())
    var path by mutableStateOf(defaultPath)
    var heading by mutableStateOf(defaultHeading)
    var dueDate by mutableStateOf(initialTask?.dueDate ?: if (initialTask == null) defaultDate else null)
    var scheduledDate by mutableStateOf(initialTask?.scheduledDate)
    var startDate by mutableStateOf(initialTask?.startDate)
    var endDate by mutableStateOf(initialTask?.endDate)
    var timeText by mutableStateOf(initialTask?.time?.toString().orEmpty())
    var recurrenceText by mutableStateOf(initialTask?.recurrence.orEmpty())
    var priority by mutableStateOf(initialTask?.priority ?: TaskPriority.Normal)
    var pickingDate by mutableStateOf<TaskDateField?>(null)

    /** Пустое поле — это «времени нет», а не ошибка; ругаемся только на набранную ерунду. */
    val time: TaskTimeSpan? get() = TaskTimeSpan.parse(timeText)
    val timeInvalid: Boolean get() = timeText.isNotBlank() && time == null

    /**
     * Конец, время и повтор без начала бессмысленны: событием таску делает именно 🛫,
     * и до него эти поля некуда приложить.
     */
    val isEvent: Boolean get() = startDate != null

    val canSubmit: Boolean get() = title.isNotBlank() && path.isNotBlank() && !timeInvalid

    /** Дата, с которой открывается календарь для выбранного поля. */
    fun initialDateFor(field: TaskDateField): CalendarDate = when (field) {
        TaskDateField.Due -> dueDate ?: scheduledDate ?: defaultDate
        TaskDateField.Scheduled -> scheduledDate ?: dueDate ?: defaultDate
        TaskDateField.Start -> startDate ?: dueDate ?: defaultDate
        TaskDateField.End -> endDate ?: startDate ?: defaultDate
    }

    fun setDate(field: TaskDateField, date: CalendarDate) {
        when (field) {
            TaskDateField.Due -> dueDate = date
            TaskDateField.Scheduled -> scheduledDate = date
            TaskDateField.Start -> startDate = date
            TaskDateField.End -> endDate = date
        }
        pickingDate = null
    }

    fun clearStartDate() {
        startDate = null
        endDate = null
    }

    fun selectDestination(destinationPath: String, destinationHeading: String?) {
        path = destinationPath
        heading = destinationHeading.orEmpty()
    }

    fun toDraft(): TaskDraft = TaskDraft(
        title = title.trim(),
        path = normalizeMarkdownPath(path),
        heading = heading.trim().ifEmpty { null },
        dueDate = dueDate,
        scheduledDate = scheduledDate,
        startDate = startDate,
        endDate = endDate.takeIf { isEvent },
        time = time.takeIf { isEvent },
        recurrence = recurrenceText.trim().ifEmpty { null }?.takeIf { isEvent },
        priority = priority,
    )
}

/**
 * Форма переживает поворот экрана: набранное в ней — это работа пользователя,
 * и терять её на смене конфигурации нельзя.
 */
private fun createTaskFormSaver(
    defaultDate: CalendarDate,
    initialTask: Task?,
): Saver<CreateTaskFormState, Any> = listSaver(
    save = { state ->
        listOf(
            state.title,
            state.path,
            state.heading,
            state.timeText,
            state.recurrenceText,
            state.priority.name,
            state.dueDate?.toEpochDay(),
            state.scheduledDate?.toEpochDay(),
            state.startDate?.toEpochDay(),
            state.endDate?.toEpochDay(),
        )
    },
    restore = { saved ->
        CreateTaskFormState(
            defaultPath = saved[1] as String,
            defaultHeading = saved[2] as String,
            defaultDate = defaultDate,
            initialTask = initialTask,
        ).apply {
            title = saved[0] as String
            timeText = saved[3] as String
            recurrenceText = saved[4] as String
            priority = TaskPriority.valueOf(saved[5] as String)
            dueDate = (saved[6] as Long?)?.let(::calendarDateFromEpochDay)
            scheduledDate = (saved[7] as Long?)?.let(::calendarDateFromEpochDay)
            startDate = (saved[8] as Long?)?.let(::calendarDateFromEpochDay)
            endDate = (saved[9] as Long?)?.let(::calendarDateFromEpochDay)
        }
    },
)

@Composable
fun rememberCreateTaskFormState(
    defaultPath: String,
    defaultHeading: String,
    defaultDate: CalendarDate,
    initialTask: Task?,
): CreateTaskFormState = rememberSaveable(
    defaultPath,
    defaultHeading,
    defaultDate,
    initialTask,
    saver = createTaskFormSaver(defaultDate, initialTask),
) {
    CreateTaskFormState(defaultPath, defaultHeading, defaultDate, initialTask)
}
