package com.akergez.shardkeep.ui.component

import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.parseTaskTitleTags
import org.jetbrains.compose.resources.DrawableResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.ic_alarm
import shardkeep.shared.generated.resources.ic_article
import shardkeep.shared.generated.resources.ic_event
import shardkeep.shared.generated.resources.ic_flight_takeoff
import shardkeep.shared.generated.resources.ic_repeat
import shardkeep.shared.generated.resources.ic_schedule
import shardkeep.shared.generated.resources.ic_tag

/** Роль чипа в палитре; конкретные цвета берёт отрисовка из темы. */
enum class TaskChipTone { Primary, Secondary, Tertiary, Neutral }

/** Готовый к отрисовке чип: иконка, подпись и роль в палитре. */
data class TaskChipModel(
    val icon: DrawableResource,
    val label: String,
    val tone: TaskChipTone,
)

/**
 * Собирает подпись задачи из её полей — здесь решается, что показать и в каком порядке,
 * а рисование остаётся в [TaskAttributes]. Порядок: когда делать, к какому сроку, чем
 * является (событие, время, повтор), чем помечено, где лежит.
 */
fun taskChips(task: Task, showLocation: Boolean): List<TaskChipModel> = buildList {
    task.scheduledDate?.let {
        add(TaskChipModel(Res.drawable.ic_schedule, it.toString(), TaskChipTone.Primary))
    }
    task.dueDate?.let {
        add(TaskChipModel(Res.drawable.ic_event, it.toString(), TaskChipTone.Secondary))
    }
    task.startDate?.let { start ->
        // Начало и конец — одна мысль, и в ленте они читаются как отрезок; два
        // отдельных чипа заставляли бы сличать даты глазами.
        val label = task.endDate?.takeIf { it != start }?.let { "$start → $it" } ?: start.toString()
        add(TaskChipModel(Res.drawable.ic_flight_takeoff, label, TaskChipTone.Primary))
    }
    task.time?.let {
        add(TaskChipModel(Res.drawable.ic_alarm, it.toString(), TaskChipTone.Secondary))
    }
    task.recurrence?.let {
        add(TaskChipModel(Res.drawable.ic_repeat, it, TaskChipTone.Neutral))
    }
    parseTaskTitleTags(task.title).tags.forEach { tag ->
        add(TaskChipModel(Res.drawable.ic_tag, tag, TaskChipTone.Tertiary))
    }
    if (showLocation) {
        add(
            TaskChipModel(
                icon = Res.drawable.ic_article,
                label = task.location.path.substringAfterLast('/').removeSuffix(".md"),
                tone = TaskChipTone.Neutral,
            ),
        )
    }
}
