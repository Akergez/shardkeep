package com.akergez.shardkeep.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.domain.model.TimeRange
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.today
import shardkeep.shared.generated.resources.week

/** Переключатель диапазона ленты: сегодня или неделя. */
@Composable
fun TimeRangeSelector(
    selected: TimeRange,
    onSelect: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == TimeRange.Day,
            onClick = { onSelect(TimeRange.Day) },
            label = { Text(stringResource(Res.string.today)) },
        )
        FilterChip(
            selected = selected == TimeRange.Week,
            onClick = { onSelect(TimeRange.Week) },
            label = { Text(stringResource(Res.string.week)) },
        )
    }
}
