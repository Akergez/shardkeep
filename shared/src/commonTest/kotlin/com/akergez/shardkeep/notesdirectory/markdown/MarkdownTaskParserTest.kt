package com.akergez.shardkeep.notesdirectory.markdown

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.TaskFormat
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.model.TaskTimeSpan
import com.akergez.shardkeep.domain.model.TimeOfDay
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTaskParserTest {
    private val parser = MarkdownTaskParser()

    @Test
    fun parsesTasksAndSourceLocation() {
        val markdown = """
            # Проект
            Текст
            - [ ] Сделать прототип 📅 2026-07-02
            * [x] Проверить результат
        """.trimIndent()

        val tasks = parser.parse(NotesDirectoryPath("Projects/App.md"), markdown)

        assertEquals(2, tasks.size)
        assertEquals("Сделать прототип", tasks[0].title)
        assertEquals(CalendarDate(2026, 7, 2), tasks[0].dueDate)
        assertEquals(TaskStatus.Todo, tasks[0].status)
        assertEquals("Проект", tasks[0].location.heading)
        assertEquals(2, tasks[0].location.lineIndex)
        assertEquals(TaskStatus.Done, tasks[1].status)
    }

    @Test
    fun ignoresFrontmatterAndFencedCodeBlocks() {
        val markdown = """
            ---
            template: "- [ ] not a task"
            ---
            ```markdown
            - [ ] example
            ```
            - [ ] real task
        """.trimIndent()

        val tasks = parser.parse(NotesDirectoryPath("Note.md"), markdown)

        assertEquals(listOf("real task"), tasks.map { it.title })
    }

    @Test
    fun supportsCrLfNestedAndNumberedLists() {
        val markdown = "## List\r\n  - [ ] nested\r\n1. [X] numbered\r\n"

        val tasks = parser.parse(NotesDirectoryPath("Note.md"), markdown)

        assertEquals(listOf("nested", "numbered"), tasks.map { it.title })
        assertEquals(listOf(TaskStatus.Todo, TaskStatus.Done), tasks.map { it.status })
    }

    @Test
    fun malformedDateDoesNotBreakIndexing() {
        val tasks = parser.parse(NotesDirectoryPath("Note.md"), "- [ ] impossible 📅 2026-99-99")

        assertEquals(1, tasks.size)
        assertEquals(null, tasks.single().dueDate)
        assertEquals("impossible", tasks.single().title)
    }

    @Test
    fun shardkeepChecklistIsNotIndexedAsTasks() {
        val markdown = "---\nshardkeep-type: checklist\n---\n# Groceries\n- [ ] Milk"

        assertEquals(emptyList(), parser.parse(NotesDirectoryPath("Shardkeep/Quick Notes/Groceries.md"), markdown))
    }

    @Test
    fun parsesEventAttributes() {
        val markdown = "- [ ] Конференция 🛫 2026-09-01 🏁 2026-09-03 ⏰ 10:00-18:00"

        val task = parser.parse(NotesDirectoryPath("Note.md"), markdown).single()

        assertEquals("Конференция", task.title)
        assertEquals(CalendarDate(2026, 9, 1), task.startDate)
        assertEquals(CalendarDate(2026, 9, 3), task.endDate)
        assertEquals(TaskTimeSpan(TimeOfDay(10, 0), TimeOfDay(18, 0)), task.time)
    }

    @Test
    fun parsesHalfOpenTimeSpans() {
        val path = NotesDirectoryPath("Note.md")

        val fromNoon = parser.parse(path, "- [ ] Дежурство 🛫 2026-09-01 ⏰ 12:00").single()
        val tillNoon = parser.parse(path, "- [ ] Дежурство 🛫 2026-09-01 ⏰ -12:00").single()

        assertEquals(TaskTimeSpan(TimeOfDay(12, 0), null), fromNoon.time)
        assertEquals(TaskTimeSpan(null, TimeOfDay(12, 0)), tillNoon.time)
        assertEquals("Дежурство", fromNoon.title)
        assertEquals("Дежурство", tillNoon.title)
    }

    @Test
    fun recurrenceTextRunsUntilTheNextField() {
        val markdown = "- [ ] Стендап 🔁 every weekday ⏰ 10:00-10:15 🛫 2026-09-01"

        val task = parser.parse(NotesDirectoryPath("Note.md"), markdown).single()

        assertEquals("every weekday", task.recurrence)
        assertEquals("Стендап", task.title)
        assertEquals(CalendarDate(2026, 9, 1), task.startDate)
    }

    @Test
    fun recurrenceTextRunsToEndOfLine() {
        val markdown = "- [ ] Ретро 🛫 2026-09-04 🔁 every 2 weeks on Friday"

        val task = parser.parse(NotesDirectoryPath("Note.md"), markdown).single()

        assertEquals("every 2 weeks on Friday", task.recurrence)
        assertEquals("Ретро", task.title)
    }

    @Test
    fun textAfterTimeStaysInTheTitle() {
        val markdown = "- [ ] Созвон ⏰ 14:00-15:00 в переговорке 🛫 2026-09-05"

        val task = parser.parse(NotesDirectoryPath("Note.md"), markdown).single()

        assertEquals("Созвон в переговорке", task.title)
        assertEquals(TaskTimeSpan(TimeOfDay(14, 0), TimeOfDay(15, 0)), task.time)
    }

    @Test
    fun parsesEventAttributesInDataviewFormat() {
        val markdown =
            "- [ ] Конференция [start:: 2026-09-01] [end:: 2026-09-03] " +
                "[time:: 10:00-18:00] [repeat:: every year]"

        val task = MarkdownTaskParser(TaskFormat.Dataview)
            .parse(NotesDirectoryPath("Note.md"), markdown)
            .single()

        assertEquals("Конференция", task.title)
        assertEquals(CalendarDate(2026, 9, 1), task.startDate)
        assertEquals(CalendarDate(2026, 9, 3), task.endDate)
        assertEquals(TaskTimeSpan(TimeOfDay(10, 0), TimeOfDay(18, 0)), task.time)
        assertEquals("every year", task.recurrence)
    }

    @Test
    fun malformedTimeIsDroppedWithoutLosingTheTask() {
        val task = parser
            .parse(NotesDirectoryPath("Note.md"), "- [ ] Встреча 🛫 2026-09-01 ⏰ 99:99")
            .single()

        assertEquals(null, task.time)
        assertEquals(CalendarDate(2026, 9, 1), task.startDate)
    }
}
