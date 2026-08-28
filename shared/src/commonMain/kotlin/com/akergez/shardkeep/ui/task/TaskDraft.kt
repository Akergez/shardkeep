package com.akergez.shardkeep.ui.task

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.TaskPriority
import com.akergez.shardkeep.domain.model.TaskTimeSpan

/** Что пользователь набрал в диалоге задачи — вход для создания или правки. */
data class TaskDraft(
    val title: String,
    val path: String,
    val heading: String?,
    val dueDate: CalendarDate?,
    val scheduledDate: CalendarDate? = null,
    val startDate: CalendarDate? = null,
    val endDate: CalendarDate? = null,
    val time: TaskTimeSpan? = null,
    val recurrence: String? = null,
    val priority: TaskPriority = TaskPriority.Normal,
)

/** Путь всегда markdown-файл в каталоге: ведущий слэш убираем, расширение дописываем. */
fun normalizeMarkdownPath(value: String): String {
    val trimmed = value.trim().trimStart('/')
    return if (trimmed.endsWith(".md", ignoreCase = true)) trimmed else "$trimmed.md"
}
