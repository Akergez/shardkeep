package com.akergez.shardkeep.widget

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.akergez.shardkeep.data.NotesDirectoryQuickNoteRepository
import com.akergez.shardkeep.data.NotesDirectoryTaskRepository
import com.akergez.shardkeep.db.DatabaseDriverFactory
import com.akergez.shardkeep.db.createDatabase
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.model.TaskFormat
import com.akergez.shardkeep.notesdirectory.AndroidSafNotesDirectoryStorage
import kotlinx.coroutines.flow.first
import com.akergez.shardkeep.WorkspaceRegistry

data class WorkspaceNotes(val workspaceId: String, val workspaceName: String, val notes: List<QuickNote>)

/** Rebuilds widget data after changes made while the application UI is not running. */
object WidgetUpdater {
    suspend fun loadNotes(context: Context): List<WorkspaceNotes> {
        val appContext = context.applicationContext
        val registry = WorkspaceRegistry(appContext)
        return registry.list().mapNotNull { workspace ->
            val quickNotesFolder = registry.preferences(workspace.id).getString("quick_notes_folder", null)
                ?: NotesDirectoryQuickNoteRepository.QUICK_NOTES_FOLDER
            val repository = NotesDirectoryQuickNoteRepository(
                AndroidSafNotesDirectoryStorage(appContext, workspace.treeUri),
                quickNotesFolder,
            )
            try {
                repository.refresh()
                val notes = repository.observeNotes().first()
                Log.i(
                    "ShardkeepWidget",
                    "Loaded ${notes.size} notes for ${workspace.name}",
                )
                WorkspaceNotes(workspace.id, workspace.name, notes)
            } catch (error: Throwable) {
                Log.e("ShardkeepWidget", "Failed to load notes for ${workspace.name}", error)
                null
            }
        }
    }

    suspend fun publishNotes(context: Context) {
        val appContext = context.applicationContext
        val notes = loadNotes(appContext)
        val manager = GlanceAppWidgetManager(appContext)
        val quickNoteWidget = QuickNoteWidget()
        val checklistWidget = ChecklistWidget()
        val noteIds = manager.getGlanceIds(QuickNoteWidget::class.java)
        val checklistIds = manager.getGlanceIds(ChecklistWidget::class.java)
        val notesJson = WidgetNoteState.encodeNotes(notes)
        val checklistsJson = WidgetNoteState.encodeChecklists(notes)
        Log.i("ShardkeepWidget", "Updating ${noteIds.size} note and ${checklistIds.size} checklist widget instances")
        noteIds.forEach { id ->
            updateAppWidgetState(appContext, id) { it[WidgetNoteState.NOTES] = notesJson }
            quickNoteWidget.update(appContext, id)
        }
        checklistIds.forEach { id ->
            updateAppWidgetState(appContext, id) { it[WidgetNoteState.CHECKLISTS] = checklistsJson }
            checklistWidget.update(appContext, id)
        }
        Log.i("ShardkeepWidget", "Glance state and update calls completed")
    }

    suspend fun publishTasks(context: Context) {
        TaskWidget().updateAll(context.applicationContext)
    }

    suspend fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        val registry = WorkspaceRegistry(appContext)
        registry.list().forEach { workspace ->
            val format = workspace.taskFormat ?: return@forEach
            NotesDirectoryTaskRepository(
                storage = AndroidSafNotesDirectoryStorage(appContext, workspace.treeUri),
                format = format,
            ).refresh()
        }
        publishTasks(appContext)
        publishNotes(appContext)
    }
}
