package com.akergez.shardkeep.domain.usecase

import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.repository.TaskDestination

/**
 * Находка поиска в разделе «Все задачи»: либо файл целиком, либо задача внутри файла.
 *
 * [path] есть у обеих: по нему экран понимает, какую вкладку открыть.
 */
sealed interface TaskSearchResult {
    val path: String

    /** Файл, чьё имя совпало с запросом. */
    data class File(override val path: String) : TaskSearchResult

    /** Задача, чей текст совпал с запросом. */
    data class Match(val task: Task) : TaskSearchResult {
        override val path: String get() = task.location.path
    }
}

/**
 * Поиск по вкладкам «Всех задач»: сперва файлы, потом задачи в порядке вкладок.
 *
 * Ищем только среди того, что экран умеет открыть — иначе по находке было бы некуда
 * перейти. Поэтому источник тот же, что у пейджера: список вкладок и их задачи.
 */
fun searchTasks(
    query: String,
    files: List<TaskDestination>,
    tasksByFile: Map<String, List<Task>>,
    limit: Int = SEARCH_RESULT_LIMIT,
): List<TaskSearchResult> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()

    val fileResults = files.asSequence()
        .filter { it.path.contains(needle, ignoreCase = true) }
        .map { TaskSearchResult.File(it.path) }
    val taskResults = files.asSequence()
        .flatMap { file -> tasksByFile[file.path].orEmpty().asSequence() }
        .filter { it.title.contains(needle, ignoreCase = true) }
        .map(TaskSearchResult::Match)

    return (fileResults + taskResults).take(limit).toList()
}

/** Список находок — подсказка, а не отчёт: длинный никто не дочитывает. */
const val SEARCH_RESULT_LIMIT: Int = 50
