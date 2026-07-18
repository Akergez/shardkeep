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
import com.akergez.shardkeep.WorkspaceRegistry

class ToggleTaskAction : ActionCallback {
    companion object {
        val TASK_ID_KEY = ActionParameters.Key<String>("task_id")
        val NEW_STATUS_KEY = ActionParameters.Key<String>("new_status")
        val WORKSPACE_ID_KEY = ActionParameters.Key<String>("workspace_id")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TASK_ID_KEY] ?: return
        val workspaceId = parameters[WORKSPACE_ID_KEY] ?: return
        val newStatus = when (parameters[NEW_STATUS_KEY]) {
            "Done" -> TaskStatus.Done
            else -> TaskStatus.Todo
        }

        val workspace = WorkspaceRegistry(context).list().firstOrNull { it.id == workspaceId } ?: return
        val format = workspace.taskFormat ?: return

        val storage = AndroidSafNotesDirectoryStorage(context, workspace.treeUri)
        val repository = NotesDirectoryTaskRepository(storage, format)

        runCatching {
            repository.refresh()
            repository.setStatus(TaskId(taskId), newStatus)
        }

        TaskWidget().updateAll(context)
    }
}
