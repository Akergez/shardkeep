package com.akergez.shardkeep.ui.component

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.calendarDateFromEpochDay
import com.akergez.shardkeep.domain.model.toEpochDay
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.cancel
import shardkeep.shared.generated.resources.ok

/** Материальный календарь, говорящий на языке [CalendarDate]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDatePickerDialog(
    initialDate: CalendarDate,
    onDismiss: () -> Unit,
    onSelect: (CalendarDate) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate.toEpochMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onSelect(calendarDateFromEpochMillis(it)) } },
            ) { Text(stringResource(Res.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

// Пикер говорит миллисекундами от эпохи в UTC, а CalendarDate — это просто день.
private fun CalendarDate.toEpochMillis(): Long = toEpochDay() * MILLIS_PER_DAY

private fun calendarDateFromEpochMillis(millis: Long): CalendarDate =
    calendarDateFromEpochDay(millis.floorDiv(MILLIS_PER_DAY))
