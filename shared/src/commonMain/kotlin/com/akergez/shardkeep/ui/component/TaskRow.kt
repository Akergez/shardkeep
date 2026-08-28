package com.akergez.shardkeep.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskStatus
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.edit_task
import shardkeep.shared.generated.resources.ic_edit

/** Строка задачи в ленте дня. */
@Composable
fun TaskRow(
    task: Task,
    onCheckedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CircleCheckbox(
            checked = task.status == TaskStatus.Done,
            onCheckedChange = onCheckedChange,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            TaskTitleText(task)
            TaskAttributes(task = task, showLocation = true)
        }
        IconButton(onClick = onEdit) {
            Icon(
                painter = painterResource(Res.drawable.ic_edit),
                contentDescription = stringResource(Res.string.edit_task),
            )
        }
    }
}
