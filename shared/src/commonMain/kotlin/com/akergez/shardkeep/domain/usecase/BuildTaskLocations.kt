package com.akergez.shardkeep.domain.usecase

import com.akergez.shardkeep.domain.model.Task

/** Места, по которым можно отфильтровать ленту: файлы и содержащие их папки. */
data class TaskLocations(
    val files: List<String> = emptyList(),
    val folders: List<String> = emptyList(),
)

/** Собирает список мест из задач: только те файлы и папки, где что-то действительно лежит. */
fun buildTaskLocations(tasks: List<Task>): TaskLocations {
    val files = tasks.map { it.location.path }.distinct().sorted()
    val folders = files.mapNotNull { path ->
        path.substringBeforeLast('/', missingDelimiterValue = "").ifEmpty { null }
    }.distinct().sorted()
    return TaskLocations(files = files, folders = folders)
}
