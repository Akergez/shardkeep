package com.akergez.shardkeep.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.choose_another_folder
import shardkeep.shared.generated.resources.indexing_notes_directory
import shardkeep.shared.generated.resources.notes_directory_open_error
import shardkeep.shared.generated.resources.notes_directory_open_error_description
import shardkeep.shared.generated.resources.retry

/** Папка выбрана, но индекс ещё не готов. */
@Composable
fun NotesDirectoryLoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.indexing_notes_directory))
    }
}

/** Папку не удалось открыть: предлагаем повторить или выбрать другую. */
@Composable
fun NotesDirectoryErrorScreen(onRetry: () -> Unit, onChooseAnother: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.notes_directory_open_error), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(Res.string.notes_directory_open_error_description),
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
            TextButton(onClick = onChooseAnother) {
                Text(stringResource(Res.string.choose_another_folder))
            }
        }
    }
}
