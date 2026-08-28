package com.akergez.shardkeep.domain.usecase

import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.repository.TaskDestination

/**
 * Вкладки экрана «все задачи»: по одному файлу на каждый, где ещё осталось незакрытое.
 *
 * Порядок — по времени последней правки файла, свежие первыми: работа обычно
 * продолжается там, где её только что оставили. Файлы, о которых хранилище ничего не
 * знает, считаются самыми старыми и разбираются по имени, чтобы порядок был устойчив.
 */
fun buildTaskFiles(tasks: List<Task>, destinations: List<TaskDestination>): List<TaskDestination> {
    val modifiedByPath = destinations.groupBy { it.path }
        .mapValues { (_, values) -> values.maxOfOrNull { it.modifiedAtMillis } ?: 0L }
    return tasks.asSequence()
        .filter { it.status == TaskStatus.Todo }
        .map { it.location.path }
        .distinct()
        .sortedWith(compareByDescending<String> { modifiedByPath[it] ?: 0L }.thenBy { it })
        .map { TaskDestination(it, modifiedAtMillis = modifiedByPath[it] ?: 0L) }
        .toList()
}
