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
import java.time.LocalDate

data class WidgetTask(
    val id: String,
    val title: String,
    val isOverdue: Boolean,
    val dateLabel: String?,
)

class TaskWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tasks = loadTasks(context)
        provideContent {
            GlanceTheme {
                TaskWidgetContent(tasks)
            }
        }
    }

    private fun loadTasks(context: Context): List<WidgetTask> {
        val prefs = context.getSharedPreferences("notes_directory", Context.MODE_PRIVATE)
        prefs.getString("tree_uri", null) ?: return emptyList()
        val db = runCatching { createDatabase(DatabaseDriverFactory(context)) }.getOrNull()
            ?: return emptyList()
        val today = LocalDate.now().toString()
        return db.indexedTaskQueries.selectAll().executeAsList()
            .mapNotNull { row ->
                val effective = row.scheduled_date ?: row.due_date ?: return@mapNotNull null
                // ISO yyyy-MM-dd compares lexicographically; keep today and anything past due.
                if (row.status == "Done" || effective > today) return@mapNotNull null
                WidgetTask(
                    id = row.id,
                    title = row.title,
                    isOverdue = effective < today,
                    dateLabel = if (effective < today) shortDate(effective) else null,
                )
            }
            // Overdue first, then the rest by date.
            .sortedWith(compareByDescending<WidgetTask> { it.isOverdue }.thenBy { it.dateLabel ?: "" })
    }

    private fun shortDate(iso: String): String {
        val parts = iso.split("-")
        return if (parts.size == 3) "${parts[2]}.${parts[1]}" else iso
    }
}

@Composable
private fun TaskWidgetContent(tasks: List<WidgetTask>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(WidgetNavigation.openTasks(context)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.widget_today),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (tasks.isNotEmpty()) {
                Text(
                    text = tasks.size.toString(),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    ),
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        if (tasks.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_no_tasks_today),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(tasks, itemId = { it.id.hashCode().toLong() }) { task ->
                    TaskRow(task)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: WidgetTask) {
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
