package com.akergez.shardkeep.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskFilter
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.usecase.DayTaskSections
import com.akergez.shardkeep.domain.usecase.TaskLocations
import com.akergez.shardkeep.ui.component.EmptyTasks
import com.akergez.shardkeep.ui.component.LocationFilter
import com.akergez.shardkeep.ui.component.TaskRow
import com.akergez.shardkeep.ui.component.TimeRangeSelector
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.done_tasks
import shardkeep.shared.generated.resources.overdue_tasks

/**
 * Лента дня: переключатели диапазона и места, дальше — задачи по секциям.
 * Раскладку по секциям делает домен, экран только рисует то, что ему дали.
 */
@Composable
fun TodayTasksScreen(
    dayTasks: DayTaskSections,
    filter: TaskFilter,
    locations: TaskLocations,
    isEmpty: Boolean,
    onFilterChange: (TaskFilter) -> Unit,
    onTaskCheckedChange: (TaskId, Boolean) -> Unit,
    onEditTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TimeRangeSelector(
            selected = filter.range,
            onSelect = { onFilterChange(filter.copy(range = it)) },
        )
        LocationFilter(
            locations = locations,
            filter = filter,
            onChange = onFilterChange,
        )
        if (isEmpty) {
            EmptyTasks(modifier = Modifier.weight(1f))
        } else {
            TaskList(
                dayTasks = dayTasks,
                onTaskCheckedChange = onTaskCheckedChange,
                onEditTask = onEditTask,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TaskList(
    dayTasks: DayTaskSections,
    onTaskCheckedChange: (TaskId, Boolean) -> Unit,
    onEditTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overdueLabel = stringResource(Res.string.overdue_tasks)
    val doneLabel = stringResource(Res.string.done_tasks)
    val overdueColor = MaterialTheme.colorScheme.error
    val doneColor = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
        if (dayTasks.overdue.isNotEmpty()) {
            sectionHeader("header_overdue", overdueLabel, overdueColor)
            taskItems(dayTasks.overdue, onTaskCheckedChange, onEditTask)
        }
        taskItems(dayTasks.active, onTaskCheckedChange, onEditTask)
        if (dayTasks.done.isNotEmpty()) {
            sectionHeader("header_done", doneLabel, doneColor)
            taskItems(dayTasks.done, onTaskCheckedChange, onEditTask)
        }
    }
}

private fun LazyListScope.sectionHeader(key: String, label: String, color: Color) {
    item(key = key) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

private fun LazyListScope.taskItems(
    tasks: List<Task>,
    onTaskCheckedChange: (TaskId, Boolean) -> Unit,
    onEditTask: (Task) -> Unit,
) {
    items(tasks, key = { it.id.value }) { task ->
        TaskRow(
            task = task,
            onCheckedChange = { checked -> onTaskCheckedChange(task.id, checked) },
            onEdit = { onEditTask(task) },
        )
        HorizontalDivider()
    }
}
