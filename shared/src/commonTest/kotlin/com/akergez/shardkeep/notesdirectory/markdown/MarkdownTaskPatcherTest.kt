package com.akergez.shardkeep.notesdirectory.markdown

import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.notesdirectory.NotesDirectoryException
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MarkdownTaskPatcherTest {
    private val parser = MarkdownTaskParser()
    private val patcher = MarkdownTaskPatcher()

    @Test
    fun changesOnlyStatusMarkerAndPreservesCrLf() {
        val original = "# Tasks\r\n- [ ] first 📅 2026-07-01\r\nText\r\n"
        val task = parser.parse(NotesDirectoryPath("Tasks.md"), original).single()

        val changed = patcher.setStatus(original, task, TaskStatus.Done)

        assertEquals("# Tasks\r\n- [x] first 📅 2026-07-01\r\nText\r\n", changed)
    }

    @Test
    fun refusesToPatchWhenSourceLineChangedExternally() {
        val original = "- [ ] original"
        val task = parser.parse(NotesDirectoryPath("Tasks.md"), original).single()

        assertFailsWith<NotesDirectoryException.Conflict> {
            patcher.setStatus("- [ ] externally edited", task, TaskStatus.Done)
        }
    }

    @Test
    fun insertsAtEndOfRequestedSection() {
        val original = "# First\nText\n## Nested\nNested text\n# Second\nText"

        val changed = patcher.insertTask(original, "First", "- [ ] new task")

        assertEquals(
            "# First\nText\n## Nested\nNested text\n- [ ] new task\n# Second\nText",
            changed,
        )
    }

    @Test
    fun appendsToDocumentWithoutHeading() {
        assertEquals("Text\n- [ ] new task", patcher.insertTask("Text", null, "- [ ] new task"))
        assertEquals("- [ ] new task", patcher.insertTask("", null, "- [ ] new task"))
    }

    @Test
    fun rejectsMissingHeadingAndInvalidTaskLine() {
        assertFailsWith<NotesDirectoryException.InvalidDocument> {
            patcher.insertTask("# Existing", "Missing", "- [ ] task")
        }
        assertFailsWith<IllegalArgumentException> {
            patcher.insertTask("", null, "plain text")
        }
    }
}
