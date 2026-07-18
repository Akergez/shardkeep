package com.akergez.shardkeep.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.akergez.shardkeep.R
import com.akergez.shardkeep.domain.model.QuickNote

data class WidgetNote(
    val workspaceId: String,
    val workspaceName: String,
    val path: String,
    val title: String,
    val content: String,
) {
    val selectionKey: String get() = "$workspaceId::$path"
}

class QuickNoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialNotes = WidgetUpdater.loadNotes(context).flatMap { source ->
            source.notes.filterIsInstance<QuickNote.Text>().map {
                WidgetNote(source.workspaceId, source.workspaceName, it.id.path, it.title, it.content)
            }
        }
        provideContent {
            GlanceTheme {
                val state = currentState<Preferences>()
                val notes = state[WidgetNoteState.NOTES]
                    ?.let(WidgetNoteState::decodeNotes)
                    ?: initialNotes
                val expanded = state[DROPDOWN_EXPANDED] ?: false
                val selected = notes.firstOrNull { it.selectionKey == state[SELECTED_PATH] }
                    ?: notes.firstOrNull()
                QuickNoteContent(notes, selected, expanded)
            }
        }
    }

}

@Composable
private fun QuickNoteContent(notes: List<WidgetNote>, selected: WidgetNote?, expanded: Boolean) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (notes.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_no_notes),
                modifier = GlanceModifier.clickable(WidgetNavigation.openNotes(context)),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
            return@Column
        }

        DropdownHeader(
            title = selected?.title ?: context.getString(R.string.widget_notes_title),
            expanded = expanded,
            toggleAction = actionRunCallback<ToggleNoteDropdownAction>(),
        )
        Spacer(GlanceModifier.height(6.dp))

        if (expanded) {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(notes, itemId = { it.selectionKey.hashCode().toLong() }) { note ->
                    PickerRow(
                        title = "${note.workspaceName} — ${note.title}",
                        isSelected = note.selectionKey == selected?.selectionKey,
                        action = actionRunCallback<SelectNoteAction>(selectParams(note.selectionKey)),
                    )
                }
            }
        } else if (selected != null && selected.content.isNotBlank()) {
            Text(
                text = selected.content,
                modifier = GlanceModifier.clickable(WidgetNavigation.openNote(context, selected.path)),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                maxLines = 8,
            )
        }
    }
}
