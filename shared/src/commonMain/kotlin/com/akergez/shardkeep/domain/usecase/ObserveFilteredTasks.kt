package com.akergez.shardkeep.domain.usecase

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskFilter
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.model.TimeRange
import com.akergez.shardkeep.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveFilteredTasks(
    private val repository: TaskRepository,
) {
    operator fun invoke(filter: TaskFilter, today: CalendarDate): Flow<List<Task>> =
        repository.observeTasks().map { tasks ->
            filterTasks(tasks, filter, today)
        }
}

fun filterTasks(tasks: List<Task>, filter: TaskFilter, today: CalendarDate): List<Task> =
    tasks.asSequence()
        .filter { task -> task.matchesLocation(filter) }
        .filter { task -> task.matchesRange(filter.range, today) }
        .sortedWith(compareBy<Task> { it.effectiveDate }.thenBy { it.title })
        .toList()

private val Task.effectiveDate: CalendarDate? get() = scheduledDate ?: dueDate

private fun Task.matchesLocation(filter: TaskFilter): Boolean =
    (filter.file == null || location.path == filter.file) &&
        (filter.folder == null || location.path.startsWith("${filter.folder.trimEnd('/')}/"))

private fun Task.matchesRange(range: TimeRange, today: CalendarDate): Boolean {
    val date = effectiveDate ?: return false
    return when (range) {
        TimeRange.Day ->
            if (status == TaskStatus.Done) date == today
            else date <= today
        TimeRange.Week ->
            status != TaskStatus.Done && date >= today && date <= today.plusDays(6)
    }
}

internal fun CalendarDate.plusDays(days: Int): CalendarDate {
    require(days >= 0)
    var year = year
    var month = month
    var day = day
    repeat(days) {
        day += 1
        val daysInMonth = when (month) {
            2 -> if (year.isLeapYear()) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        if (day > daysInMonth) {
            day = 1
            month += 1
            if (month > 12) {
                month = 1
                year += 1
            }
        }
    }
    return CalendarDate(year, month, day)
}

private fun Int.isLeapYear(): Boolean = this % 400 == 0 || (this % 4 == 0 && this % 100 != 0)
