package com.akergez.shardkeep.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.akergez.shardkeep.R
import com.akergez.shardkeep.domain.model.QuickNote

data class WidgetChecklistItem(val index: Int, val text: String, val isChecked: Boolean)
data class WidgetChecklist(
    val workspaceId: String,
    val workspaceName: String,
    val path: String,
    val title: String,
    val items: List<WidgetChecklistItem>,
) {
    val selectionKey: String get() = "$workspaceId::$path"
}

class ChecklistWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(250.dp, 220.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialChecklists = WidgetUpdater.loadNotes(context).flatMap { source ->
            source.notes.filterIsInstance<QuickNote.Checklist>().map { checklist ->
                WidgetChecklist(
                    workspaceId = source.workspaceId,
                    workspaceName = source.workspaceName,
                    path = checklist.id.path,
                    title = checklist.title,
                    items = checklist.items.map { item ->
                        WidgetChecklistItem(item.index, item.text, item.isChecked)
                    },
                )
            }
        }
        Log.i("ShardkeepWidget", "Loaded initial checklist widget state with ${initialChecklists.size} checklists")
        provideContent {
            GlanceTheme {
                val state = currentState<Preferences>()
                val checklists = state[WidgetNoteState.CHECKLISTS]
                    ?.let(WidgetNoteState::decodeChecklists)
                    ?: initialChecklists
                Log.i("ShardkeepWidget", "Rendering checklist widget with ${checklists.size} checklists")
                val expanded = state[DROPDOWN_EXPANDED] ?: false
                val selected = checklists.firstOrNull { it.selectionKey == state[SELECTED_PATH] }
                    ?: checklists.firstOrNull()
                val isLarge = LocalSize.current.width >= 200.dp
                ChecklistContent(checklists, selected, expanded, isLarge)
            }
        }
    }

}

@Composable
private fun ChecklistContent(
    checklists: List<WidgetChecklist>,
    selected: WidgetChecklist?,
    expanded: Boolean,
    isLarge: Boolean,
) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = if (isLarge) 16.dp else 12.dp, vertical = 12.dp),
    ) {
        if (checklists.isEmpty()) {
            Text(
                text = "✓",
                style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp),
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = context.getString(R.string.widget_no_checklists),
                modifier = GlanceModifier.clickable(WidgetNavigation.openNotes(context)),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            return@Column
        }

        DropdownHeader(
            title = selected?.title ?: context.getString(R.string.widget_checklists_title),
            expanded = expanded,
            toggleAction = actionRunCallback<ToggleChecklistDropdownAction>(),
        )
        Spacer(GlanceModifier.height(6.dp))

        if (expanded) {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(checklists, itemId = { it.selectionKey.hashCode().toLong() }) { checklist ->
                    PickerRow(
                        title = "${checklist.workspaceName} — ${checklist.title}",
                        isSelected = checklist.selectionKey == selected?.selectionKey,
                        action = actionRunCallback<SelectChecklistAction>(selectParams(checklist.selectionKey)),
                    )
                }
            }
        } else if (isLarge) {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(selected?.items ?: emptyList(), itemId = { it.index.toLong() }) { item ->
                    ChecklistRow(selected!!.workspaceId, selected.path, item)
                }
            }
        } else {
            val remaining = selected?.items?.count { !it.isChecked } ?: 0
            Text(
                text = context.getString(R.string.widget_items_remaining, remaining),
                modifier = GlanceModifier.clickable(
                    WidgetNavigation.openNote(context, selected?.path ?: "")
                ),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun ChecklistRow(workspaceId: String, path: String, item: WidgetChecklistItem) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .clickable(
                    actionRunCallback<ToggleChecklistItemAction>(
                        androidx.glance.action.actionParametersOf(
                            ToggleChecklistItemAction.NOTE_PATH_KEY to path,
                            ToggleChecklistItemAction.WORKSPACE_ID_KEY to workspaceId,
                            ToggleChecklistItemAction.ITEM_INDEX_KEY to item.index,
                            ToggleChecklistItemAction.IS_CHECKED_KEY to !item.isChecked,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (item.isChecked) "✓" else "○",
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 18.sp),
            )
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = item.text,
            style = TextStyle(
                color = if (item.isChecked) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            maxLines = 1,
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(WidgetNavigation.openNote(context, path)),
        )
    }
}
