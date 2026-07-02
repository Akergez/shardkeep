package com.akergez.shardkeep.data

import com.akergez.shardkeep.db.ShardkeepDatabase
import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.NotesDirectoryLocation
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskFormat
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskPriority
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.repository.TaskRepository
import com.akergez.shardkeep.domain.repository.TaskDestination
import com.akergez.shardkeep.domain.repository.TaskDestinationProvider
import com.akergez.shardkeep.notesdirectory.FileVersion
import com.akergez.shardkeep.notesdirectory.NotesDirectoryException
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath
import com.akergez.shardkeep.notesdirectory.NotesDirectoryStorage
import com.akergez.shardkeep.notesdirectory.markdown.MarkdownTaskParser
import com.akergez.shardkeep.notesdirectory.markdown.MarkdownTaskPatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class NotesDirectoryTaskRepository(
    private val storage: NotesDirectoryStorage,
    val format: TaskFormat = TaskFormat.Emoji,
    private val parser: MarkdownTaskParser = MarkdownTaskParser(format),
    private val patcher: MarkdownTaskPatcher = MarkdownTaskPatcher(),
    private val database: ShardkeepDatabase? = null,
) : TaskRepository, TaskDestinationProvider {
    private val tasks = MutableStateFlow<List<Task>>(emptyList())
    private val indexMutex = Mutex()
    private val readSemaphore = Semaphore(8)
    private var index = emptyMap<NotesDirectoryPath, IndexedFile>()

    override fun observeTasks(): Flow<List<Task>> = tasks.asStateFlow()

    override suspend fun refresh() = indexMutex.withLock {
        if (index.isEmpty()) {
            index = loadFromDb()
        }
        val files = storage.listMarkdownFiles().filterNot {
            it.path.value.startsWith("${NotesDirectoryQuickNoteRepository.QUICK_NOTES_FOLDER}/")
        }
        val refreshed = coroutineScope {
            files.map { file ->
                async {
                    val cached = index[file.path]
                    file.path to if (cached?.listingVersion == file.version) {
                        cached
                    } else {
                        readSemaphore.withPermit {
                            val document = storage.read(file.path)
                            val (parsedTasks, headings) = parser.parseWithHeadings(document.path, document.content)
                            IndexedFile(
                                listingVersion = file.version,
                                tasks = parsedTasks,
                                headings = headings,
                            )
                        }
                    }
                }
            }.awaitAll()
        }.toMap()
        index = refreshed
        tasks.value = refreshed.values.flatMap { it.tasks }
        saveToDb(refreshed)
    }

    override suspend fun listDestinations(): List<TaskDestination> {
        refresh()
        return indexMutex.withLock {
            index.flatMap { (path, indexedFile) ->
            buildList {
                    add(TaskDestination(path.value))
                    indexedFile.headings.forEach { heading ->
                        add(TaskDestination(path.value, heading))
                    }
                }
            }.distinct()
        }
    }

    override suspend fun setStatus(id: TaskId, status: TaskStatus) {
        val indexedTask = tasks.value.firstOrNull { it.id == id }
            ?: throw NotesDirectoryException.InvalidDocument("Task is absent from the current index")
        val path = NotesDirectoryPath(indexedTask.location.path)
        val document = storage.read(path)
        val currentTask = parser.parse(path, document.content).firstOrNull {
            it.location.sourceLine == indexedTask.location.sourceLine && it.title == indexedTask.title
        } ?: throw NotesDirectoryException.Conflict(path)
        val changed = patcher.setStatus(document.content, currentTask, status)
        storage.write(path, document.version, changed)
        invalidate(path)
        refresh()
    }

    override suspend fun setDueDate(id: TaskId, date: CalendarDate) {
        val indexedTask = tasks.value.firstOrNull { it.id == id }
            ?: throw NotesDirectoryException.InvalidDocument("Task is absent from the current index")
        val path = NotesDirectoryPath(indexedTask.location.path)
        val document = storage.read(path)
        val currentTask = parser.parse(path, document.content).firstOrNull {
            it.location.sourceLine == indexedTask.location.sourceLine && it.title == indexedTask.title
        } ?: throw NotesDirectoryException.Conflict(path)
        val sourceLine = currentTask.location.sourceLine!!
        val dateStr = date.toString()
        val newLine = when (format) {
            TaskFormat.Emoji -> {
                val regex = Regex("📅\\s*\\d{4}-\\d{2}-\\d{2}")
                if (regex.containsMatchIn(sourceLine)) sourceLine.replace(regex, "📅 $dateStr")
                else "$sourceLine 📅 $dateStr"
            }
            TaskFormat.Dataview -> {
                val regex = Regex("""[\[(]due::\s*\d{4}-\d{2}-\d{2}[\])]""")
                if (regex.containsMatchIn(sourceLine)) sourceLine.replace(regex, "[due:: $dateStr]")
                else "$sourceLine [due:: $dateStr]"
            }
        }
        val changed = patcher.replaceLine(document.content, currentTask, newLine)
        storage.write(path, document.version, changed)
        invalidate(path)
        refresh()
    }

    override suspend fun setScheduledDate(id: TaskId, date: CalendarDate) {
        val indexedTask = tasks.value.firstOrNull { it.id == id }
            ?: throw NotesDirectoryException.InvalidDocument("Task is absent from the current index")
        val path = NotesDirectoryPath(indexedTask.location.path)
        val document = storage.read(path)
        val currentTask = parser.parse(path, document.content).firstOrNull {
            it.location.sourceLine == indexedTask.location.sourceLine && it.title == indexedTask.title
        } ?: throw NotesDirectoryException.Conflict(path)
        val sourceLine = currentTask.location.sourceLine!!
        val dateStr = date.toString()
        val newLine = when (format) {
            TaskFormat.Emoji -> {
                val regex = Regex("⏳\\s*\\d{4}-\\d{2}-\\d{2}")
                if (regex.containsMatchIn(sourceLine)) sourceLine.replace(regex, "⏳ $dateStr")
                else "$sourceLine ⏳ $dateStr"
            }
            TaskFormat.Dataview -> {
                val regex = Regex("""[\[(]scheduled::\s*\d{4}-\d{2}-\d{2}[\])]""")
                if (regex.containsMatchIn(sourceLine)) sourceLine.replace(regex, "[scheduled:: $dateStr]")
                else "$sourceLine [scheduled:: $dateStr]"
            }
        }
        val changed = patcher.replaceLine(document.content, currentTask, newLine)
        storage.write(path, document.version, changed)
        invalidate(path)
        refresh()
    }

    override suspend fun create(task: Task) {
        val path = NotesDirectoryPath(task.location.path)
        val taskLine = buildTaskLine(task)
        val document = runCatching { storage.read(path) }.getOrElse { error ->
            if (error is NotesDirectoryException.NotFound) {
                val initial = buildString {
                    task.location.heading?.let { append("# ").append(it).append('\n') }
                    append(taskLine)
                }
                storage.create(path, initial)
                invalidate(path)
                refresh()
                return
            }
            throw error
        }
        val changed = try {
            patcher.insertTask(document.content, task.location.heading, taskLine)
        } catch (error: NotesDirectoryException.InvalidDocument) {
            if (task.location.heading == null) throw error
            document.content.trimEnd() + "\n\n# ${task.location.heading}\n$taskLine"
        }
        storage.write(path, document.version, changed)
        invalidate(path)
        refresh()
    }

    private fun buildTaskLine(task: Task): String = buildString {
        append("- [")
        append(if (task.status == TaskStatus.Done) 'x' else ' ')
        append("] ")
        append(task.title)
        when (format) {
            TaskFormat.Emoji -> {
                if (task.priority != TaskPriority.Normal) append(" ").append(task.priority.emoji)
                task.scheduledDate?.let { append(" ⏳ ").append(it) }
                task.dueDate?.let { append(" 📅 ").append(it) }
            }
            TaskFormat.Dataview -> {
                task.scheduledDate?.let { append(" [scheduled:: ").append(it).append("]") }
                task.dueDate?.let { append(" [due:: ").append(it).append("]") }
                if (task.priority != TaskPriority.Normal) {
                    append(" [priority:: ").append(task.priority.dataviewName).append("]")
                }
            }
        }
    }

    private suspend fun invalidate(path: NotesDirectoryPath) = indexMutex.withLock {
        index = index - path
    }

    private fun loadFromDb(): Map<NotesDirectoryPath, IndexedFile> {
        val db = database ?: return emptyMap()
        return db.indexedFileQueries.selectAll().executeAsList().associate { row ->
            val fileTasks = db.indexedTaskQueries.selectByFile(row.path).executeAsList().mapNotNull { t ->
                runCatching {
                    Task(
                        id = TaskId(t.id),
                        title = t.title,
                        status = if (t.status == "Done") TaskStatus.Done else TaskStatus.Todo,
                        dueDate = t.due_date?.toCalendarDate(),
                        scheduledDate = t.scheduled_date?.toCalendarDate(),
                        priority = runCatching { TaskPriority.valueOf(t.priority) }.getOrDefault(TaskPriority.Normal),
                        location = NotesDirectoryLocation(
                            path = t.file_path,
                            heading = t.heading,
                            lineIndex = t.line_index.toInt(),
                            sourceLine = t.source_line,
                        ),
                    )
                }.getOrNull()
            }
            val headings = if (row.headings.isEmpty()) emptyList() else row.headings.split('\n')
            NotesDirectoryPath(row.path) to IndexedFile(
                listingVersion = FileVersion(row.listing_version),
                tasks = fileTasks,
                headings = headings,
            )
        }
    }

    private fun saveToDb(index: Map<NotesDirectoryPath, IndexedFile>) {
        val db = database ?: return
        db.transaction {
            db.indexedFileQueries.deleteAll()
            db.indexedTaskQueries.deleteAll()
            for ((path, indexedFile) in index) {
                db.indexedFileQueries.upsertFile(
                    path = path.value,
                    listing_version = indexedFile.listingVersion.value,
                    indexed_at = 0L,
                    headings = indexedFile.headings.joinToString("\n"),
                )
                indexedFile.tasks.forEach { task ->
                    db.indexedTaskQueries.insertTask(
                        id = task.id.value,
                        file_path = path.value,
                        line_index = task.location.lineIndex?.toLong() ?: 0L,
                        source_line = task.location.sourceLine ?: "",
                        heading = task.location.heading,
                        title = task.title,
                        status = task.status.name,
                        due_date = task.dueDate?.toString(),
                        scheduled_date = task.scheduledDate?.toString(),
                        priority = task.priority.name,
                    )
                }
            }
        }
    }

    private fun String.toCalendarDate(): CalendarDate? = runCatching {
        val parts = split('-')
        CalendarDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }.getOrNull()

    private data class IndexedFile(
        val listingVersion: FileVersion,
        val tasks: List<Task>,
        val headings: List<String>,
    )
}
