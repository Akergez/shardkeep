package com.akergez.shardkeep.domain.usecase

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.NotesDirectoryLocation
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskFilter
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.model.TimeRange
import com.akergez.shardkeep.domain.model.plusDays
import kotlin.test.Test
import kotlin.test.assertEquals

class FilterTasksTest {
    private val today = CalendarDate(2026, 7, 1)

    @Test
    fun dayIncludesOverdueAndTodayButNotFutureOrUndated() {
        val result = filterTasks(
            tasks = listOf(
                task("overdue", CalendarDate(2026, 6, 30)),
                task("today", today),
                task("future", CalendarDate(2026, 7, 2)),
                task("undated", null),
            ),
            filter = TaskFilter(TimeRange.Day),
            today = today,
        )

        assertEquals(listOf("overdue", "today"), result.map { it.title })
    }

    @Test
    fun weekIncludesTodayThroughSixDaysAhead() {
        val result = filterTasks(
            tasks = listOf(
                task("overdue", CalendarDate(2026, 6, 30)),
                task("today", today),
                task("last day", CalendarDate(2026, 7, 7)),
                task("next week", CalendarDate(2026, 7, 8)),
            ),
            filter = TaskFilter(TimeRange.Week),
            today = today,
        )

        assertEquals(listOf("today", "last day"), result.map { it.title })
    }

    @Test
    fun locationFiltersCanBeCombined() {
        val tasks = listOf(
            task("work", today, "Work/Today.md"),
            task("home", today, "Home/Today.md"),
        )

        assertEquals(
            listOf("work"),
            filterTasks(tasks, TaskFilter(folder = "Work"), today).map { it.title },
        )
        assertEquals(
            listOf("home"),
            filterTasks(tasks, TaskFilter(file = "Home/Today.md"), today).map { it.title },
        )
    }

    @Test
    fun dateArithmeticCrossesMonthAndLeapYear() {
        assertEquals(CalendarDate(2024, 2, 29), CalendarDate(2024, 2, 28).plusDays(1))
        assertEquals(CalendarDate(2026, 1, 1), CalendarDate(2025, 12, 31).plusDays(1))
    }

    private fun task(
        title: String,
        date: CalendarDate?,
        path: String = "Inbox.md",
    ) = Task(
        id = TaskId(title),
        title = title,
        status = TaskStatus.Todo,
        dueDate = date,
        location = NotesDirectoryLocation(path),
    )
}
