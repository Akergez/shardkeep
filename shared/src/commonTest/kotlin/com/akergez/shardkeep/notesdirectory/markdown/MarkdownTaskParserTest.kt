package com.akergez.shardkeep.notesdirectory.markdown

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.TaskStatus
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
}
