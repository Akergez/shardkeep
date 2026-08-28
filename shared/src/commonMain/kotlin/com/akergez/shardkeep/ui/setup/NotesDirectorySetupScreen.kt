package com.akergez.shardkeep.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.choose_folder
import shardkeep.shared.generated.resources.connect_notes_directory
import shardkeep.shared.generated.resources.connect_notes_directory_description

/** Первый экран: пока папка с заметками не выбрана, показывать нечего. */
@Composable
fun NotesDirectorySetupScreen(onChooseNotesDirectory: () -> Unit) {
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
            Text(stringResource(Res.string.connect_notes_directory), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(Res.string.connect_notes_directory_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onChooseNotesDirectory) { Text(stringResource(Res.string.choose_folder)) }
        }
    }
}
