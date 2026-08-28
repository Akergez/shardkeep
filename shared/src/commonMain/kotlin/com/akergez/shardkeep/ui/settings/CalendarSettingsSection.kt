package com.akergez.shardkeep.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.calendar_auto_sync
import shardkeep.shared.generated.resources.calendar_description
import shardkeep.shared.generated.resources.calendar_error
import shardkeep.shared.generated.resources.calendar_export_ics
import shardkeep.shared.generated.resources.calendar_never_synced
import shardkeep.shared.generated.resources.calendar_sync_now
import shardkeep.shared.generated.resources.calendar_title

/**
 * Календарь: тумблер автоматической выгрузки, кнопка «прямо сейчас» и запасной
 * выход в `.ics` для тех, у кого календарь живёт не на этом устройстве.
 */
@Composable
fun ColumnScope.CalendarSettingsSection(callbacks: SettingsCallbacks) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val errorLabel = stringResource(Res.string.calendar_error)

    fun run(action: suspend () -> String?) {
        if (busy) return
        scope.launch {
            busy = true
            message = runCatching { action() }.fold(
                onSuccess = { it },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    error.message?.takeIf { it.isNotBlank() } ?: errorLabel
                },
            )
            busy = false
        }
    }

    Text(stringResource(Res.string.calendar_title), style = MaterialTheme.typography.titleMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.calendar_auto_sync),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(Res.string.calendar_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = callbacks.calendarSyncEnabled,
            onCheckedChange = callbacks.onCalendarSyncEnabledChange,
        )
    }
    Text(
        text = message
            ?: callbacks.calendarLastSync
            ?: stringResource(Res.string.calendar_never_synced),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        callbacks.onCalendarSyncNow?.let { syncNow ->
            Button(onClick = { run { syncNow() } }, enabled = !busy) {
                Text(stringResource(Res.string.calendar_sync_now))
            }
        }
        callbacks.onCalendarExport?.let { export ->
            OutlinedButton(onClick = { run { export() } }, enabled = !busy) {
                Text(stringResource(Res.string.calendar_export_ics))
            }
        }
    }
}
