package com.akergez.shardkeep.domain.usecase

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.effectiveDate
import com.akergez.shardkeep.domain.model.plusDays
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskFilter
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.model.TimeRange

/** Лента для выбранного диапазона и места: что показать и в каком порядке. */
fun filterTasks(tasks: List<Task>, filter: TaskFilter, today: CalendarDate): List<Task> =
    tasks.asSequence()
        .filter { task -> task.matchesLocation(filter) }
        .filter { task -> task.matchesRange(filter.range, today) }
        .sortedWith(compareBy<Task> { it.effectiveDate }.thenBy { it.title })
        .toList()

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
