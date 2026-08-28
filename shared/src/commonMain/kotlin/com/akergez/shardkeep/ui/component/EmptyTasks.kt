package com.akergez.shardkeep.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.no_tasks
import shardkeep.shared.generated.resources.no_tasks_hint

/** Заглушка пустой ленты задач. */
@Composable
fun EmptyTasks(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.no_tasks), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.no_tasks_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
