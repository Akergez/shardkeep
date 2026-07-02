package com.akergez.shardkeep.notesdirectory.markdown

import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath
import kotlin.test.Test
import kotlin.test.assertEquals

class QuickNoteMarkdownCodecTest {
    private val codec = QuickNoteMarkdownCodec()

    @Test
    fun textNoteRoundTrips() {
        val markdown = codec.encodeText("Idea", "First line\nSecond line")
        val note = codec.decode(NotesDirectoryPath("Shardkeep/Quick Notes/Idea.md"), markdown) as QuickNote.Text

        assertEquals("Idea", note.title)
        assertEquals("First line\nSecond line", note.content)
    }

    @Test
    fun checklistRoundTripsAndTogglesOneItem() {
        val original = codec.encodeChecklist("Groceries", listOf("Milk", "Bread"))
        val changed = codec.setChecklistItem(
            original,
            NotesDirectoryPath("Shardkeep/Quick Notes/Groceries.md"),
            itemIndex = 1,
            checked = true,
        )
        val checklist = codec.decode(
            NotesDirectoryPath("Shardkeep/Quick Notes/Groceries.md"),
            changed,
        ) as QuickNote.Checklist

        assertEquals(listOf(false, true), checklist.items.map { it.isChecked })
        assertEquals(listOf("Milk", "Bread"), checklist.items.map { it.text })
    }
}
