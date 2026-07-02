package com.akergez.shardkeep.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/** Per-widget-instance state: which item is shown and whether the picker is open. */
val SELECTED_PATH = stringPreferencesKey("selected_path")
val DROPDOWN_EXPANDED = booleanPreferencesKey("dropdown_expanded")

/** Parameter carrying the chosen item's path into a Select*Action. */
val SELECT_PATH_KEY = ActionParameters.Key<String>("path")

fun selectParams(path: String) = actionParametersOf(SELECT_PATH_KEY to path)

class ToggleChecklistDropdownAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[DROPDOWN_EXPANDED] = !(it[DROPDOWN_EXPANDED] ?: false) }
        ChecklistWidget().update(context, glanceId)
    }
}

class SelectChecklistAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val path = parameters[SELECT_PATH_KEY] ?: return
        updateAppWidgetState(context, glanceId) {
            it[SELECTED_PATH] = path
            it[DROPDOWN_EXPANDED] = false
        }
        ChecklistWidget().update(context, glanceId)
    }
}

class ToggleNoteDropdownAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[DROPDOWN_EXPANDED] = !(it[DROPDOWN_EXPANDED] ?: false) }
        QuickNoteWidget().update(context, glanceId)
    }
}

class SelectNoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val path = parameters[SELECT_PATH_KEY] ?: return
        updateAppWidgetState(context, glanceId) {
            it[SELECTED_PATH] = path
            it[DROPDOWN_EXPANDED] = false
        }
        QuickNoteWidget().update(context, glanceId)
    }
}
