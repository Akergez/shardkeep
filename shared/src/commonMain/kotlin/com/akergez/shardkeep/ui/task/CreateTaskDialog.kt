package com.akergez.shardkeep.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.repository.TaskDestination
import com.akergez.shardkeep.domain.repository.TaskDestinationProvider
import com.akergez.shardkeep.domain.model.TaskPriority
import com.akergez.shardkeep.ui.component.CalendarDatePickerDialog
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.cancel
import shardkeep.shared.generated.resources.choose_existing_location
import shardkeep.shared.generated.resources.clear
import shardkeep.shared.generated.resources.create
import shardkeep.shared.generated.resources.due_date
import shardkeep.shared.generated.resources.edit_task
import shardkeep.shared.generated.resources.end_date
import shardkeep.shared.generated.resources.event_time
import shardkeep.shared.generated.resources.event_time_hint
import shardkeep.shared.generated.resources.file_path
import shardkeep.shared.generated.resources.heading_optional
import shardkeep.shared.generated.resources.invalid_time
import shardkeep.shared.generated.resources.new_task
import shardkeep.shared.generated.resources.not_set
import shardkeep.shared.generated.resources.priority
import shardkeep.shared.generated.resources.priority_high
import shardkeep.shared.generated.resources.priority_low
import shardkeep.shared.generated.resources.priority_normal
import shardkeep.shared.generated.resources.recurrence
import shardkeep.shared.generated.resources.recurrence_hint
import shardkeep.shared.generated.resources.save
import shardkeep.shared.generated.resources.scheduled_date
import shardkeep.shared.generated.resources.start_date
import shardkeep.shared.generated.resources.task_title_label

/**
 * Диалог создания и редактирования задачи. Отвечает только за вёрстку:
 * все поля и правила живут в [CreateTaskFormState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskDialog(
    destinationProvider: TaskDestinationProvider?,
    defaultPath: String,
    defaultHeading: String,
    defaultDate: CalendarDate,
    onDismiss: () -> Unit,
    onCreate: (TaskDraft) -> Unit,
    fixedPath: String? = null,
    initialTask: Task? = null,
) {
    val form = rememberCreateTaskFormState(
        defaultPath = defaultPath,
        defaultHeading = defaultHeading,
        defaultDate = defaultDate,
        initialTask = initialTask,
    )
    var destinations by remember { mutableStateOf<List<TaskDestination>>(emptyList()) }
    var destinationMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(destinationProvider) {
        destinations = runCatching { destinationProvider?.listDestinations().orEmpty() }
            .getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initialTask == null) Res.string.new_task else Res.string.edit_task))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { form.title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.task_title_label)) },
                    singleLine = true,
                )
                if (fixedPath == null && destinations.isNotEmpty()) {
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
                                        form.selectDestination(destination.path, destination.heading)
                                        destinationMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (fixedPath == null) {
                    OutlinedTextField(
                        value = form.path,
                        onValueChange = { form.path = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.file_path)) },
                        singleLine = true,
                    )
                } else {
                    Text(
                        text = fixedPath.substringAfterLast('/').removeSuffix(".md"),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                OutlinedTextField(
                    value = form.heading,
                    onValueChange = { form.heading = it },
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
                        selected = form.priority == TaskPriority.Low,
                        onClick = { form.priority = TaskPriority.Low },
                        label = { Text(stringResource(Res.string.priority_low)) },
                    )
                    FilterChip(
                        selected = form.priority == TaskPriority.Normal,
                        onClick = { form.priority = TaskPriority.Normal },
                        label = { Text(stringResource(Res.string.priority_normal)) },
                    )
                    FilterChip(
                        selected = form.priority == TaskPriority.High,
                        onClick = { form.priority = TaskPriority.High },
                        label = { Text(stringResource(Res.string.priority_high)) },
                    )
                }
                TaskDateRow(
                    label = stringResource(Res.string.due_date),
                    date = form.dueDate,
                    onPick = { form.pickingDate = TaskDateField.Due },
                    onClear = { form.dueDate = null },
                )
                TaskDateRow(
                    label = stringResource(Res.string.scheduled_date),
                    date = form.scheduledDate,
                    onPick = { form.pickingDate = TaskDateField.Scheduled },
                    onClear = { form.scheduledDate = null },
                )
                TaskDateRow(
                    label = stringResource(Res.string.start_date),
                    date = form.startDate,
                    onPick = { form.pickingDate = TaskDateField.Start },
                    onClear = { form.clearStartDate() },
                )
                if (form.isEvent) {
                    TaskDateRow(
                        label = stringResource(Res.string.end_date),
                        date = form.endDate,
                        onPick = { form.pickingDate = TaskDateField.End },
                        onClear = { form.endDate = null },
                    )
                    OutlinedTextField(
                        value = form.timeText,
                        onValueChange = { form.timeText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.event_time)) },
                        placeholder = { Text(stringResource(Res.string.event_time_hint)) },
                        isError = form.timeInvalid,
                        supportingText = if (form.timeInvalid) {
                            { Text(stringResource(Res.string.invalid_time)) }
                        } else {
                            null
                        },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = form.recurrenceText,
                        onValueChange = { form.recurrenceText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.recurrence)) },
                        placeholder = { Text(stringResource(Res.string.recurrence_hint)) },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = form.canSubmit,
                onClick = { onCreate(form.toDraft()) },
            ) { Text(stringResource(if (initialTask == null) Res.string.create else Res.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )

    form.pickingDate?.let { field ->
        CalendarDatePickerDialog(
            initialDate = form.initialDateFor(field),
            onDismiss = { form.pickingDate = null },
            onSelect = { form.setDate(field, it) },
        )
    }
}

/** Строка «поле-дата»: кнопка с текущим значением и крестик, когда есть что чистить. */
@Composable
private fun TaskDateRow(
    label: String,
    date: CalendarDate?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            Text("$label: " + (date?.toString() ?: stringResource(Res.string.not_set)))
        }
        if (date != null) {
            TextButton(onClick = onClear) { Text(stringResource(Res.string.clear)) }
        }
    }
}
