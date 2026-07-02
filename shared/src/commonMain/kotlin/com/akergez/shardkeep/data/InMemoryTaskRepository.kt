package com.akergez.shardkeep.data

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemoryTaskRepository(initialTasks: List<Task> = emptyList()) : TaskRepository {
    private val tasks = MutableStateFlow(initialTasks)

    override fun observeTasks(): Flow<List<Task>> = tasks

    override suspend fun setStatus(id: TaskId, status: TaskStatus) {
        tasks.update { current ->
            current.map { task -> if (task.id == id) task.copy(status = status) else task }
        }
    }

    override suspend fun setDueDate(id: TaskId, date: CalendarDate) {
        tasks.update { current ->
            current.map { task -> if (task.id == id) task.copy(dueDate = date) else task }
        }
    }

    override suspend fun setScheduledDate(id: TaskId, date: CalendarDate) {
        tasks.update { current ->
            current.map { task -> if (task.id == id) task.copy(scheduledDate = date) else task }
        }
    }

    override suspend fun create(task: Task) {
        tasks.update { current -> current + task }
    }
}
