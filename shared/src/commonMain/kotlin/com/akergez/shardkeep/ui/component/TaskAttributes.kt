package com.akergez.shardkeep.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.domain.model.Task
import org.jetbrains.compose.resources.painterResource

/** Строка чипов под заголовком задачи. Что рисовать, решает [taskChips]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskAttributes(
    task: Task,
    showLocation: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val chips = remember(task, showLocation) { taskChips(task, showLocation) }
    if (chips.isEmpty()) return

    FlowRow(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chips.forEach { chip ->
            val colors = MaterialTheme.colorScheme
            val container = when (chip.tone) {
                TaskChipTone.Primary -> colors.primaryContainer
                TaskChipTone.Secondary -> colors.secondaryContainer
                TaskChipTone.Tertiary -> colors.tertiaryContainer
                TaskChipTone.Neutral -> colors.surfaceContainerHighest
            }
            val content = when (chip.tone) {
                TaskChipTone.Primary -> colors.onPrimaryContainer
                TaskChipTone.Secondary -> colors.onSecondaryContainer
                TaskChipTone.Tertiary -> colors.onTertiaryContainer
                TaskChipTone.Neutral -> colors.onSurfaceVariant
            }
            TaskChip(
                painter = painterResource(chip.icon),
                label = chip.label,
                containerColor = container,
                contentColor = content,
            )
        }
    }
}
