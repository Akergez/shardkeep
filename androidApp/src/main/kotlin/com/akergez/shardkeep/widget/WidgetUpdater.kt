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

/** Rebuilds widget data after changes made while the application UI is not running. */
object WidgetUpdater {
    suspend fun loadNotes(context: Context): List<QuickNote> {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("notes_directory", Context.MODE_PRIVATE)
        val treeUri = prefs.getString("tree_uri", null)?.let(Uri::parse) ?: return emptyList()
        val quickNotesFolder = prefs.getString("quick_notes_folder", null)
            ?: NotesDirectoryQuickNoteRepository.QUICK_NOTES_FOLDER
        val repository = NotesDirectoryQuickNoteRepository(
            AndroidSafNotesDirectoryStorage(appContext, treeUri),
            quickNotesFolder,
        )
        return try {
            repository.refresh()
            repository.observeNotes().first().also { notes ->
                Log.i(
                    "ShardkeepWidget",
                    "Loaded ${notes.size} notes from SAF (${notes.count { it is QuickNote.Checklist }} checklists)",
                )
            }
        } catch (error: Throwable) {
            Log.e("ShardkeepWidget", "Failed to load widget notes from SAF", error)
            throw error
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
        val prefs = appContext.getSharedPreferences("notes_directory", Context.MODE_PRIVATE)
        val treeUri = prefs.getString("tree_uri", null)?.let(Uri::parse) ?: return
        val taskFormat = prefs.getString("task_format", null)
            ?.let { runCatching { TaskFormat.valueOf(it) }.getOrNull() }
            ?: return
        val quickNotesFolder = prefs.getString("quick_notes_folder", null)
            ?: NotesDirectoryQuickNoteRepository.QUICK_NOTES_FOLDER
        val storage = AndroidSafNotesDirectoryStorage(appContext, treeUri)

        val taskRepository = NotesDirectoryTaskRepository(
            storage = storage,
            format = taskFormat,
            database = createDatabase(DatabaseDriverFactory(appContext)),
        )
        taskRepository.refresh()
        publishTasks(appContext)
        publishNotes(appContext)
    }
}
