package com.akergez.shardkeep

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.akergez.shardkeep.data.InMemoryTaskRepository
import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.NotesDirectoryLocation
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.repository.TaskRepository
import com.akergez.shardkeep.platform.rememberCurrentLocalDate
import com.akergez.shardkeep.ui.ShardkeepApp
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.default_tasks_heading
import shardkeep.shared.generated.resources.demo_check_ui
import shardkeep.shared.generated.resources.demo_connect_notes_directory

@Composable
@Preview
fun App() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            val today = rememberCurrentLocalDate()
            val demoConnectNotesDirectory = stringResource(Res.string.demo_connect_notes_directory)
            val demoCheckUi = stringResource(Res.string.demo_check_ui)
            val defaultHeading = stringResource(Res.string.default_tasks_heading)
            val repository = remember(today, demoConnectNotesDirectory, demoCheckUi, defaultHeading) {
                demoRepository(today, demoConnectNotesDirectory, demoCheckUi, defaultHeading)
            }
            ShardkeepApp(repository = repository, today = today)
        }
    }
}

private fun demoRepository(
    today: CalendarDate,
    connectNotesDirectoryTitle: String,
    checkUiTitle: String,
    defaultHeading: String,
): TaskRepository = InMemoryTaskRepository(
    listOf(
        Task(
            id = TaskId("demo-1"),
            title = connectNotesDirectoryTitle,
            status = TaskStatus.Todo,
            dueDate = today,
            location = NotesDirectoryLocation("Shardkeep/Inbox.md", defaultHeading),
        ),
        Task(
            id = TaskId("demo-2"),
            title = checkUiTitle,
            status = TaskStatus.Done,
            dueDate = today,
            location = NotesDirectoryLocation("Shardkeep/Inbox.md", defaultHeading),
        ),
    ),
)
