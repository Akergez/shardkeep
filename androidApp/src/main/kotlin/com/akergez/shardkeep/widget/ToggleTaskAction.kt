package com.akergez.shardkeep.widget

import android.content.Context
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.akergez.shardkeep.data.NotesDirectoryTaskRepository
import com.akergez.shardkeep.db.DatabaseDriverFactory
import com.akergez.shardkeep.db.createDatabase
import com.akergez.shardkeep.domain.model.TaskFormat
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.notesdirectory.AndroidSafNotesDirectoryStorage

class ToggleTaskAction : ActionCallback {
    companion object {
        val TASK_ID_KEY = ActionParameters.Key<String>("task_id")
        val NEW_STATUS_KEY = ActionParameters.Key<String>("new_status")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TASK_ID_KEY] ?: return
        val newStatus = when (parameters[NEW_STATUS_KEY]) {
            "Done" -> TaskStatus.Done
            else -> TaskStatus.Todo
        }

        val prefs = context.getSharedPreferences("notes_directory", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("tree_uri", null) ?: return
        val formatStr = prefs.getString("task_format", null) ?: return
        val format = runCatching { TaskFormat.valueOf(formatStr) }.getOrNull() ?: return

        val storage = AndroidSafNotesDirectoryStorage(context, Uri.parse(uriStr))
        val database = createDatabase(DatabaseDriverFactory(context))
        val repository = NotesDirectoryTaskRepository(storage, format, database = database)

        runCatching {
            repository.refresh()
            repository.setStatus(TaskId(taskId), newStatus)
        }

        TaskWidget().updateAll(context)
    }
}
