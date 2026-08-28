package com.akergez.shardkeep.ui.alltasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.ui.component.CircleCheckbox
import com.akergez.shardkeep.ui.component.TaskAttributes
import com.akergez.shardkeep.ui.component.TaskTitleText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.edit_task
import shardkeep.shared.generated.resources.ic_edit
import shardkeep.shared.generated.resources.ic_event_available
import shardkeep.shared.generated.resources.schedule_today

/**
 * Строка задачи в разделе «Все задачи»: без места (оно и так в заголовке вкладки),
 * зато с кнопкой «на сегодня» — за ней сюда чаще всего и приходят.
 *
 * [highlighted] помечает задачу, за которой пришли из поиска: вкладку с ней открыли
 * целиком, и без подсветки среди соседей её пришлось бы искать заново.
 */
@Composable
fun AllTaskRow(
    task: Task,
    today: CalendarDate,
    onCheckedChange: (Boolean) -> Unit,
    onScheduleToday: () -> Unit,
    onEdit: () -> Unit,
    highlighted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlighted) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleCheckbox(
            checked = task.status == TaskStatus.Done,
            onCheckedChange = onCheckedChange,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            TaskTitleText(task)
            TaskAttributes(task = task, showLocation = false)
        }
        if (task.scheduledDate != today && task.status != TaskStatus.Done) {
            FilledTonalIconButton(onClick = onScheduleToday) {
                Icon(
                    painter = painterResource(Res.drawable.ic_event_available),
                    contentDescription = stringResource(Res.string.schedule_today),
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                painter = painterResource(Res.drawable.ic_edit),
                contentDescription = stringResource(Res.string.edit_task),
            )
        }
    }
}
