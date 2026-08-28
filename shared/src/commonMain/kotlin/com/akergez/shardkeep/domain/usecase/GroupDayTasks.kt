package com.akergez.shardkeep.domain.usecase

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.model.TimeRange
import com.akergez.shardkeep.domain.model.effectiveDate

/**
 * Лента дня, разложенная по секциям, в порядке их появления на экране.
 * Пустая секция означает, что заголовок для неё рисовать не нужно.
 */
data class DayTaskSections(
    val overdue: List<Task> = emptyList(),
    val active: List<Task> = emptyList(),
    val done: List<Task> = emptyList(),
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && active.isEmpty() && done.isEmpty()

    /** Пройтись по всем секциям, не меняя раскладки — например, наложить незаписанные правки. */
    fun map(transform: (Task) -> Task): DayTaskSections = DayTaskSections(
        overdue = overdue.map(transform),
        active = active.map(transform),
        done = done.map(transform),
    )
}

/**
 * Делит уже отфильтрованную ленту на «просрочено / сегодня / сделано».
 *
 * Просроченное выделяется только в дневном диапазоне: в недельном будущие даты и так
 * идут по возрастанию, и отдельная секция там значила бы «просрочено на этой неделе»,
 * чего в ленте нет. Задача попадает ровно в одну секцию — просроченная не дублируется
 * в основном списке.
 */
fun groupDayTasks(tasks: List<Task>, today: CalendarDate, range: TimeRange): DayTaskSections {
    val overdue = if (range == TimeRange.Day) {
        tasks.filter { task -> task.effectiveDate?.let { it < today } == true }
    } else {
        emptyList()
    }
    val overdueIds = overdue.mapTo(HashSet()) { it.id }
    return DayTaskSections(
        overdue = overdue,
        active = tasks.filter { it.id !in overdueIds && it.status != TaskStatus.Done },
        done = tasks.filter { it.status == TaskStatus.Done },
    )
}
