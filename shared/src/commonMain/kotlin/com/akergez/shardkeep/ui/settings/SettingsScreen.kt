package com.akergez.shardkeep.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.no_settings_available
import shardkeep.shared.generated.resources.notifications
import shardkeep.shared.generated.resources.notifications_description
import shardkeep.shared.generated.resources.quick_notes_folder
import shardkeep.shared.generated.resources.quick_notes_folder_hint
import shardkeep.shared.generated.resources.save
import shardkeep.shared.generated.resources.settings

/**
 * Экран настроек. Сам по себе он только собирает блоки в столбец: платформенная
 * начинка приходит в [SettingsCallbacks], а какой блок показывать — решают её поля.
 */
@Composable
fun SettingsScreen(
    callbacks: SettingsCallbacks?,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (callbacks == null) {
            Text(
                text = stringResource(Res.string.no_settings_available),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        QuickNotesFolderSetting(callbacks, onSaved)

        HorizontalDivider()

        SyncSettingsSection(callbacks, onSaved)

        HorizontalDivider()

        NotificationsSetting(callbacks)

        if (callbacks.calendarAvailable) {
            HorizontalDivider()
            CalendarSettingsSection(callbacks)
        }
    }
}

/** Папка быстрых заметок: кнопка сохранения появляется, только когда есть что сохранять. */
@Composable
private fun ColumnScope.QuickNotesFolderSetting(callbacks: SettingsCallbacks, onSaved: () -> Unit) {
    var folderDraft by rememberSaveable(callbacks.quickNotesFolder) {
        mutableStateOf(callbacks.quickNotesFolder)
    }
    val folderChanged = folderDraft.trim() != callbacks.quickNotesFolder

    OutlinedTextField(
        value = folderDraft,
        onValueChange = { folderDraft = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.quick_notes_folder)) },
        placeholder = { Text(stringResource(Res.string.quick_notes_folder_hint)) },
        singleLine = true,
    )
    if (folderChanged) {
        Button(
            onClick = {
                callbacks.onFolderChange(folderDraft.trim())
                onSaved()
            },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(Res.string.save))
        }
    }
}

@Composable
private fun NotificationsSetting(callbacks: SettingsCallbacks) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.notifications),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(Res.string.notifications_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = callbacks.notificationsEnabled,
            onCheckedChange = callbacks.onNotificationsEnabledChange,
        )
    }
}
