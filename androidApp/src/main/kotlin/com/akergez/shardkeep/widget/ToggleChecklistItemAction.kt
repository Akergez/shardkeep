package com.akergez.shardkeep.widget

import android.content.Context
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.akergez.shardkeep.data.NotesDirectoryQuickNoteRepository
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.model.QuickNoteId
import com.akergez.shardkeep.notesdirectory.AndroidSafNotesDirectoryStorage
import com.akergez.shardkeep.WorkspaceRegistry

class ToggleChecklistItemAction : ActionCallback {
    companion object {
        val NOTE_PATH_KEY = ActionParameters.Key<String>("note_path")
        val ITEM_INDEX_KEY = ActionParameters.Key<Int>("item_index")
        val IS_CHECKED_KEY = ActionParameters.Key<Boolean>("is_checked")
        val WORKSPACE_ID_KEY = ActionParameters.Key<String>("workspace_id")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val notePath = parameters[NOTE_PATH_KEY] ?: return
        val itemIndex = parameters[ITEM_INDEX_KEY] ?: return
        val isChecked = parameters[IS_CHECKED_KEY] ?: return
        val workspaceId = parameters[WORKSPACE_ID_KEY] ?: return

        val registry = WorkspaceRegistry(context)
        val workspace = registry.list().firstOrNull { it.id == workspaceId } ?: return
        val prefs = registry.preferences(workspaceId)
        val folder = prefs.getString("quick_notes_folder", null)

        val storage = AndroidSafNotesDirectoryStorage(context, workspace.treeUri)
        val repo = if (folder != null) NotesDirectoryQuickNoteRepository(storage, folder)
        else NotesDirectoryQuickNoteRepository(storage)

        runCatching {
            repo.refresh()
            repo.setChecklistItem(QuickNoteId(notePath), itemIndex, isChecked)
        }

        WidgetUpdater.publishNotes(context)
    }
}
