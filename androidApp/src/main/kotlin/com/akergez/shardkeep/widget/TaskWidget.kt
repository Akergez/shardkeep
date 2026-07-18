package com.akergez.shardkeep.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.currentState
import androidx.glance.background
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
import androidx.glance.text.TextStyle
import com.akergez.shardkeep.db.DatabaseDriverFactory
import com.akergez.shardkeep.db.createDatabase
import com.akergez.shardkeep.R
import com.akergez.shardkeep.WorkspaceRegistry
import com.akergez.shardkeep.data.NotesDirectoryTaskRepository
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.notesdirectory.AndroidSafNotesDirectoryStorage
import kotlinx.coroutines.flow.first
import java.time.LocalDate

data class WidgetTask(
    val id: String,
    val title: String,
    val isOverdue: Boolean,
    val dateLabel: String?,
)

data class WidgetTaskWorkspace(val id: String, val name: String, val tasks: List<WidgetTask>)

class TaskWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val workspaces = loadTasks(context)
        provideContent {
            GlanceTheme {
                val state = currentState<Preferences>()
                val selected = workspaces.firstOrNull { it.id == state[SELECTED_WORKSPACE_ID] }
                    ?: workspaces.firstOrNull()
                TaskWidgetContent(
                    workspaces = workspaces,
                    selected = selected,
                    expanded = state[DROPDOWN_EXPANDED] ?: false,
                )
            }
        }
    }

    private suspend fun loadTasks(context: Context): List<WidgetTaskWorkspace> {
        val today = LocalDate.now().toString()
        return WorkspaceRegistry(context).list().mapNotNull { workspace ->
            val format = workspace.taskFormat ?: return@mapNotNull null
            val repository = NotesDirectoryTaskRepository(
                AndroidSafNotesDirectoryStorage(context, workspace.treeUri),
                format,
            )
            runCatching { repository.refresh() }.getOrElse { return@mapNotNull null }
            val tasks = repository.observeTasks().first().mapNotNull { task ->
                val effective = (task.scheduledDate ?: task.dueDate)?.toString() ?: return@mapNotNull null
                if (task.status == TaskStatus.Done || effective > today) return@mapNotNull null
                WidgetTask(
                    id = task.id.value,
                    title = task.title,
                    isOverdue = effective < today,
                    dateLabel = if (effective < today) shortDate(effective) else null,
                )
            }
                .sortedWith(compareByDescending<WidgetTask> { it.isOverdue }.thenBy { it.dateLabel ?: "" })
            WidgetTaskWorkspace(workspace.id, workspace.name, tasks)
        }
    }

    private fun shortDate(iso: String): String {
        val parts = iso.split("-")
        return if (parts.size == 3) "${parts[2]}.${parts[1]}" else iso
    }
}

@Composable
private fun TaskWidgetContent(
    workspaces: List<WidgetTaskWorkspace>,
    selected: WidgetTaskWorkspace?,
    expanded: Boolean,
) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        DropdownHeader(
            title = selected?.name ?: context.getString(R.string.widget_today),
            expanded = expanded,
            toggleAction = actionRunCallback<ToggleTaskWorkspaceDropdownAction>(),
        )
        Spacer(GlanceModifier.height(8.dp))
        if (expanded) {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(workspaces, itemId = { it.id.hashCode().toLong() }) { workspace ->
                    PickerRow(
                        title = workspace.name,
                        isSelected = workspace.id == selected?.id,
                        action = actionRunCallback<SelectTaskWorkspaceAction>(selectWorkspaceParams(workspace.id)),
                    )
                }
            }
            return@Column
        }
        val tasks = selected?.tasks.orEmpty()
        if (tasks.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_no_tasks_today),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(tasks, itemId = { it.id.hashCode().toLong() }) { task ->
                    TaskRow(selected!!.id, task)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(workspaceId: String, task: WidgetTask) {
    val context = LocalContext.current
    val titleColor = if (task.isOverdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurface
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .clickable(
                    actionRunCallback<ToggleTaskAction>(
                        actionParametersOf(
                            ToggleTaskAction.TASK_ID_KEY to task.id,
                            ToggleTaskAction.NEW_STATUS_KEY to "Done",
                            ToggleTaskAction.WORKSPACE_ID_KEY to workspaceId,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "○", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 18.sp))
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = task.title,
            style = TextStyle(color = titleColor, fontSize = 14.sp),
            maxLines = 1,
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(WidgetNavigation.openTasks(context)),
        )
        if (task.dateLabel != null) {
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = task.dateLabel,
                style = TextStyle(
                    color = GlanceTheme.colors.error,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}
