package com.akergez.shardkeep.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.domain.model.TaskFilter
import com.akergez.shardkeep.domain.usecase.TaskLocations
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.all_locations
import shardkeep.shared.generated.resources.file_filter
import shardkeep.shared.generated.resources.folder_filter
import shardkeep.shared.generated.resources.location_filter
import shardkeep.shared.generated.resources.location_filter_value

/**
 * Выбор места: весь каталог, папка или отдельный файл. Список мест приходит готовым —
 * собирать его из задач здесь было бы работой не по адресу.
 */
@Composable
fun LocationFilter(
    locations: TaskLocations,
    filter: TaskFilter,
    onChange: (TaskFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when {
        filter.file != null -> stringResource(Res.string.file_filter, filter.file)
        filter.folder != null -> stringResource(Res.string.folder_filter, filter.folder)
        else -> stringResource(Res.string.all_locations)
    }

    Box(modifier = modifier.padding(horizontal = 16.dp)) {
        TextButton(onClick = { expanded = true }) {
            Text(
                stringResource(
                    Res.string.location_filter_value,
                    stringResource(Res.string.location_filter),
                    label,
                ),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.all_locations)) },
                onClick = {
                    onChange(filter.copy(folder = null, file = null))
                    expanded = false
                },
            )
            locations.folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.folder_filter, folder)) },
                    onClick = {
                        onChange(filter.copy(folder = folder, file = null))
                        expanded = false
                    },
                )
            }
            locations.files.forEach { file ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.file_filter, file)) },
                    onClick = {
                        onChange(filter.copy(folder = null, file = file))
                        expanded = false
                    },
                )
            }
        }
    }
}
