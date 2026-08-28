package com.akergez.shardkeep.domain.usecase

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.NotesDirectoryLocation
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.repository.TaskDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTasksTest {
    private val files = listOf(
        TaskDestination("Work/Inbox.md", modifiedAtMillis = 2),
        TaskDestination("Home/Garden.md", modifiedAtMillis = 1),
    )
    private val tasksByFile = mapOf(
        "Work/Inbox.md" to listOf(task("write the report"), task("call the office")),
        "Home/Garden.md" to listOf(task("water the plants", "Home/Garden.md")),
    )

    @Test
    fun emptyQueryFindsNothing() {
        assertTrue(searchTasks("   ", files, tasksByFile).isEmpty())
    }

    @Test
    fun filesComeBeforeTasksAndBothMatchIgnoringCase() {
        val results = searchTasks("garden", files, tasksByFile)

        assertEquals(listOf(TaskSearchResult.File("Home/Garden.md")), results)
    }

    @Test
    fun taskTextMatchesCarryTheirFile() {
        val results = searchTasks("THE", files, tasksByFile)

        assertEquals(
            listOf("write the report", "call the office", "water the plants"),
            results.map { (it as TaskSearchResult.Match).task.title },
        )
        assertEquals(
            listOf("Work/Inbox.md", "Work/Inbox.md", "Home/Garden.md"),
            results.map { it.path },
        )
    }

    @Test
    fun fileAndTaskMatchesShareOneList() {
        val results = searchTasks("inbox", files, mapOf("Work/Inbox.md" to listOf(task("sort inbox"))))

        assertEquals(2, results.size)
        assertEquals(TaskSearchResult.File("Work/Inbox.md"), results.first())
        assertEquals("sort inbox", (results.last() as TaskSearchResult.Match).task.title)
    }

    @Test
    fun tasksOfUnlistedFilesStayOutOfResults() {
        val results = searchTasks(
            query = "plants",
            files = listOf(files.first()),
            tasksByFile = tasksByFile,
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun longListsAreCutToTheLimit() {
        val many = List(SEARCH_RESULT_LIMIT + 10) { task("task $it") }

        val results = searchTasks("task", files, mapOf("Work/Inbox.md" to many))

        assertEquals(SEARCH_RESULT_LIMIT, results.size)
    }

    private fun task(title: String, path: String = "Work/Inbox.md"): Task = Task(
        id = TaskId(title),
        title = title,
        status = TaskStatus.Todo,
        dueDate = CalendarDate(2026, 7, 1),
        location = NotesDirectoryLocation(path),
    )
}
