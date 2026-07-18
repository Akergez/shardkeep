package com.akergez.shardkeep.domain.repository

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasks(): Flow<List<Task>>

    suspend fun refresh() = Unit

    suspend fun setStatus(id: TaskId, status: TaskStatus)

    suspend fun setDueDate(id: TaskId, date: CalendarDate) = Unit

    suspend fun setScheduledDate(id: TaskId, date: CalendarDate) = Unit

    suspend fun create(task: Task)

    suspend fun update(task: Task) = Unit
}

data class TaskDestination(
    val path: String,
    val heading: String? = null,
    val modifiedAtMillis: Long = 0,
)

interface TaskDestinationProvider {
    suspend fun listDestinations(): List<TaskDestination>
}
