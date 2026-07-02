package com.akergez.shardkeep.notesdirectory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NotesDirectoryPathTest {
    @Test
    fun acceptsRelativePath() {
        assertEquals("Projects/App.md", NotesDirectoryPath("Projects/App.md").value)
    }

    @Test
    fun rejectsAbsoluteAndEscapingPaths() {
        assertFailsWith<IllegalArgumentException> { NotesDirectoryPath("/outside.md") }
        assertFailsWith<IllegalArgumentException> { NotesDirectoryPath("../outside.md") }
        assertFailsWith<IllegalArgumentException> { NotesDirectoryPath("folder/../outside.md") }
    }
}
