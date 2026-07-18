package com.akergez.shardkeep.widget

import android.content.Context
import android.util.Log
import com.akergez.shardkeep.data.NotesDirectoryTaskRepository
import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.ChecklistItem
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.model.QuickNoteId
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.repository.QuickNoteRepository
import com.akergez.shardkeep.domain.repository.TaskDestination
import com.akergez.shardkeep.domain.repository.TaskDestinationProvider
import com.akergez.shardkeep.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow

class WidgetUpdatingQuickNoteRepository(
    private val delegate: QuickNoteRepository,
    context: Context,
) : QuickNoteRepository {
    private val appContext = context.applicationContext

    override fun observeNotes(): Flow<List<QuickNote>> = delegate.observeNotes()

    override suspend fun refresh() = mutate { delegate.refresh() }
    override suspend fun createTextNote(title: String, content: String) =
        mutate { delegate.createTextNote(title, content) }
    override suspend fun createChecklist(title: String, items: List<String>) =
        mutate { delegate.createChecklist(title, items) }
    override suspend fun updateTextNote(noteId: QuickNoteId, title: String, content: String) =
        mutate { delegate.updateTextNote(noteId, title, content) }
    override suspend fun updateChecklist(noteId: QuickNoteId, title: String, items: List<ChecklistItem>) =
        mutate { delegate.updateChecklist(noteId, title, items) }
    override suspend fun setChecklistItem(noteId: QuickNoteId, itemIndex: Int, isChecked: Boolean) =
        mutate { delegate.setChecklistItem(noteId, itemIndex, isChecked) }
    override suspend fun delete(noteId: QuickNoteId) = mutate { delegate.delete(noteId) }

    private suspend inline fun mutate(operation: () -> Unit) {
        operation()
        Log.i("ShardkeepWidget", "Quick-note mutation persisted; requesting widget update")
        WidgetUpdater.publishNotes(appContext)
        Log.i("ShardkeepWidget", "Quick-note widget update request completed")
    }
}

class WidgetUpdatingTaskRepository(
    private val delegate: NotesDirectoryTaskRepository,
    context: Context,
) : TaskRepository, TaskDestinationProvider {
    private val appContext = context.applicationContext

    override fun observeTasks(): Flow<List<Task>> = delegate.observeTasks()
    override suspend fun refresh() = mutate { delegate.refresh() }
    override suspend fun setStatus(id: TaskId, status: TaskStatus) = mutate { delegate.setStatus(id, status) }
    override suspend fun setDueDate(id: TaskId, date: CalendarDate) = mutate { delegate.setDueDate(id, date) }
    override suspend fun setScheduledDate(id: TaskId, date: CalendarDate) =
        mutate { delegate.setScheduledDate(id, date) }
    override suspend fun create(task: Task) = mutate { delegate.create(task) }
    override suspend fun update(task: Task) = mutate { delegate.update(task) }
    override suspend fun listDestinations(): List<TaskDestination> = delegate.listDestinations()

    private suspend inline fun mutate(operation: () -> Unit) {
        operation()
        WidgetUpdater.publishTasks(appContext)
    }
}
