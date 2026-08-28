package com.akergez.shardkeep.data

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.NotesDirectoryLocation
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskFormat
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.model.TaskTimeSpan
import com.akergez.shardkeep.domain.model.TimeOfDay
import com.akergez.shardkeep.domain.repository.TaskDestination
import com.akergez.shardkeep.notesdirectory.FileVersion
import com.akergez.shardkeep.notesdirectory.NotesDirectoryChange
import com.akergez.shardkeep.notesdirectory.NotesDirectoryDocument
import com.akergez.shardkeep.notesdirectory.NotesDirectoryException
import com.akergez.shardkeep.notesdirectory.NotesDirectoryFile
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath
import com.akergez.shardkeep.notesdirectory.NotesDirectoryStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotesDirectoryTaskRepositoryTest {
    @Test
    fun refreshAndStatusChangeRoundTripThroughMarkdown() = runTest {
        val storage = FakeNotesDirectoryStorage(mapOf("Tasks.md" to "- [ ] test 📅 2026-07-01"))
        val repository = NotesDirectoryTaskRepository(storage)
        repository.refresh()
        val task = repository.observeTasks().first().single()

        repository.setStatus(task.id, TaskStatus.Done)

        assertEquals("- [x] test 📅 2026-07-01", storage.content("Tasks.md"))
        assertEquals(TaskStatus.Done, repository.observeTasks().first().single().status)
    }

    @Test
    fun createBuildsMissingFileAndHeading() = runTest {
        val storage = FakeNotesDirectoryStorage(emptyMap())
        val repository = NotesDirectoryTaskRepository(storage)

        repository.create(
            Task(
                id = TaskId("new"),
                title = "created",
                status = TaskStatus.Todo,
                dueDate = CalendarDate(2026, 7, 2),
                location = NotesDirectoryLocation("Shardkeep/Inbox.md", "Задачи"),
            ),
        )

        assertEquals(
            "# Задачи\n- [ ] created 📅 2026-07-02",
            storage.content("Shardkeep/Inbox.md"),
        )
        assertEquals("created", repository.observeTasks().first().single().title)
    }

    @Test
    fun eventAttributesRoundTripThroughMarkdown() = runTest {
        val storage = FakeNotesDirectoryStorage(emptyMap())
        val repository = NotesDirectoryTaskRepository(storage)
        val event = Task(
            id = TaskId("new"),
            title = "Конференция",
            status = TaskStatus.Todo,
            dueDate = null,
            startDate = CalendarDate(2026, 9, 1),
            endDate = CalendarDate(2026, 9, 3),
            time = TaskTimeSpan(TimeOfDay(10, 0), TimeOfDay(18, 0)),
            recurrence = "every year",
            location = NotesDirectoryLocation("Plan.md"),
        )

        repository.create(event)

        assertEquals(
            "- [ ] Конференция 🔁 every year 🛫 2026-09-01 🏁 2026-09-03 ⏰ 10:00-18:00",
            storage.content("Plan.md"),
        )
        val indexed = repository.observeTasks().first().single()
        assertEquals(event.startDate, indexed.startDate)
        assertEquals(event.endDate, indexed.endDate)
        assertEquals(event.time, indexed.time)
        assertEquals(event.recurrence, indexed.recurrence)
        assertEquals(event.title, indexed.title)
    }

    @Test
    fun eventAttributesRoundTripInDataviewFormat() = runTest {
        val storage = FakeNotesDirectoryStorage(emptyMap())
        val repository = NotesDirectoryTaskRepository(storage, TaskFormat.Dataview)

        repository.create(
            Task(
                id = TaskId("new"),
                title = "Конференция",
                status = TaskStatus.Todo,
                dueDate = null,
                startDate = CalendarDate(2026, 9, 1),
                endDate = CalendarDate(2026, 9, 3),
                time = TaskTimeSpan(null, TimeOfDay(18, 0)),
                location = NotesDirectoryLocation("Plan.md"),
            ),
        )

        val indexed = repository.observeTasks().first().single()
        assertEquals(CalendarDate(2026, 9, 1), indexed.startDate)
        assertEquals(CalendarDate(2026, 9, 3), indexed.endDate)
        assertEquals(TaskTimeSpan(null, TimeOfDay(18, 0)), indexed.time)
        assertEquals("Конференция", indexed.title)
    }

    @Test
    fun destinationsIncludeFilesAndHeadingsEvenWithoutTasks() = runTest {
        val storage = FakeNotesDirectoryStorage(
            mapOf(
                "Project.md" to "# Project\n## Tasks\nText",
                "Inbox.md" to "Text",
            ),
        )
        val repository = NotesDirectoryTaskRepository(storage)

        assertEquals(
            listOf(
                TaskDestination("Project.md"),
                TaskDestination("Project.md", "Project"),
                TaskDestination("Project.md", "Tasks"),
                TaskDestination("Inbox.md"),
            ),
            repository.listDestinations(),
        )
    }

    @Test
    fun refreshReadsOnlyNewOrChangedFilesAndDropsDeletedFiles() = runTest {
        val storage = FakeNotesDirectoryStorage(
            mapOf(
                "One.md" to "- [ ] one",
                "Two.md" to "- [ ] two",
            ),
        )
        val repository = NotesDirectoryTaskRepository(storage)

        repository.refresh()
        repository.refresh()
        assertEquals(2, storage.readCount)

        storage.replaceExternally("One.md", "- [ ] changed")
        storage.deleteExternally("Two.md")
        repository.refresh()

        assertEquals(3, storage.readCount)
        assertEquals(listOf("changed"), repository.observeTasks().first().map { it.title })
    }

    @Test
    fun quickNotesAndChecklistsPersistInNotesDirectory() = runTest {
        val storage = FakeNotesDirectoryStorage(emptyMap())
        val repository = NotesDirectoryQuickNoteRepository(storage)

        repository.createTextNote("Idea", "Useful text")
        repository.createChecklist("Groceries", listOf("Milk", "Bread"))
        val notes = repository.observeNotes().first()

        assertEquals(2, notes.size)
        val checklist = notes.filterIsInstance<QuickNote.Checklist>().single()
        repository.setChecklistItem(checklist.id, 0, true)
        assertEquals(
            true,
            repository.observeNotes().first().filterIsInstance<QuickNote.Checklist>()
                .single().items.first().isChecked,
        )
    }

    @Test
    fun quickNoteFolderIsExcludedFromTaskIndexAndDestinations() = runTest {
        val storage = FakeNotesDirectoryStorage(
            mapOf(
                "Shardkeep/Quick Notes/List.md" to
                    "---\nshardkeep-type: checklist\n---\n# List\n- [ ] item",
            ),
        )
        val repository = NotesDirectoryTaskRepository(storage)

        repository.refresh()

        assertEquals(emptyList(), repository.observeTasks().first())
        assertEquals(emptyList(), repository.listDestinations())
    }
}

private class FakeNotesDirectoryStorage(initial: Map<String, String>) : NotesDirectoryStorage {
    private data class Entry(var content: String, var revision: Int = 1)

    private val entries = initial.mapValues { Entry(it.value) }.toMutableMap()
    private val changes = MutableSharedFlow<NotesDirectoryChange>()
    var readCount: Int = 0
        private set

    fun content(path: String): String = entries.getValue(path).content

    fun replaceExternally(path: String, content: String) {
        entries.getValue(path).apply {
            this.content = content
            revision += 1
        }
    }

    fun deleteExternally(path: String) {
        entries.remove(path)
    }

    override suspend fun listMarkdownFiles(): List<NotesDirectoryFile> = entries.map { (path, entry) ->
        NotesDirectoryFile(NotesDirectoryPath(path), FileVersion(entry.revision.toString()), entry.content.length.toLong())
    }

    override suspend fun read(path: NotesDirectoryPath): NotesDirectoryDocument {
        readCount += 1
        val entry = entries[path.value] ?: throw NotesDirectoryException.NotFound(path)
        return NotesDirectoryDocument(path, FileVersion(entry.revision.toString()), entry.content)
    }

    override suspend fun write(
        path: NotesDirectoryPath,
        expectedVersion: FileVersion,
        content: String,
    ): FileVersion {
        val entry = entries[path.value] ?: throw NotesDirectoryException.NotFound(path)
        if (entry.revision.toString() != expectedVersion.value) throw NotesDirectoryException.Conflict(path)
        entry.content = content
        entry.revision += 1
        return FileVersion(entry.revision.toString())
    }

    override suspend fun create(path: NotesDirectoryPath, content: String): FileVersion {
        if (entries.containsKey(path.value)) throw NotesDirectoryException.Conflict(path)
        entries[path.value] = Entry(content)
        return FileVersion("1")
    }

    override suspend fun delete(path: NotesDirectoryPath) {
        entries.remove(path.value) ?: throw NotesDirectoryException.NotFound(path)
        changes.tryEmit(NotesDirectoryChange.Deleted(path))
    }

    override fun observeChanges(): Flow<NotesDirectoryChange> = changes
}
