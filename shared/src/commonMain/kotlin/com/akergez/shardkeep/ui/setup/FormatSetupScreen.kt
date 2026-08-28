package com.akergez.shardkeep.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.domain.model.TaskFormat
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.format_continue
import shardkeep.shared.generated.resources.format_dataview
import shardkeep.shared.generated.resources.format_dataview_description
import shardkeep.shared.generated.resources.format_emoji
import shardkeep.shared.generated.resources.format_emoji_description
import shardkeep.shared.generated.resources.format_select_description
import shardkeep.shared.generated.resources.format_select_title

/** Выбор формата задач в markdown: эмодзи или dataview-поля. */
@Composable
fun FormatSetupScreen(onFormatSelected: (TaskFormat) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(TaskFormat.Emoji) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.format_select_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(Res.string.format_select_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FormatOption(
                    name = stringResource(Res.string.format_emoji),
                    description = stringResource(Res.string.format_emoji_description),
                    selected = selected == TaskFormat.Emoji,
                    onClick = { selected = TaskFormat.Emoji },
                )
                FormatOption(
                    name = stringResource(Res.string.format_dataview),
                    description = stringResource(Res.string.format_dataview_description),
                    selected = selected == TaskFormat.Dataview,
                    onClick = { selected = TaskFormat.Dataview },
                )
            }
            Button(onClick = { onFormatSelected(selected) }) {
                Text(stringResource(Res.string.format_continue))
            }
        }
    }
}

@Composable
private fun FormatOption(
    name: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
