package com.akergez.shardkeep.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.akergez.shardkeep.MainActivity

/**
 * Builds Glance actions that open [MainActivity] on a specific element.
 *
 * A unique [Intent.setData] URI per target is required so Android does not
 * collapse the widgets' PendingIntents (extras are ignored for equality).
 */
object WidgetNavigation {
    const val EXTRA_NAV = "shardkeep_nav"
    const val EXTRA_NOTE_PATH = "shardkeep_note_path"

    const val NAV_TASKS = "tasks"
    const val NAV_NOTES = "notes"
    const val NAV_NOTE = "note"

    fun openTasks(context: Context): Action = build(context, NAV_TASKS, null)

    fun openNotes(context: Context): Action = build(context, NAV_NOTES, null)

    fun openNote(context: Context, path: String): Action = build(context, NAV_NOTE, path)

    private fun build(context: Context, nav: String, notePath: String?): Action {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("shardkeep://$nav/${Uri.encode(notePath.orEmpty())}")
            putExtra(EXTRA_NAV, nav)
            notePath?.let { putExtra(EXTRA_NOTE_PATH, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return actionStartActivity(intent)
    }
}
