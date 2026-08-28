package com.akergez.shardkeep

import com.akergez.shardkeep.domain.model.TaskTitleSegment
import com.akergez.shardkeep.domain.model.parseTaskTitleLinks
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskTitleLinksTest {
    @Test
    fun formatsMarkdownLinkUsingItsLabel() {
        assertEquals(
            listOf(
                TaskTitleSegment("Read "),
                TaskTitleSegment("documentation", "https://example.com/docs"),
                TaskTitleSegment(" today"),
            ),
            parseTaskTitleLinks("Read [documentation](https://example.com/docs) today"),
        )
    }

    @Test
    fun formatsBareLinkWithoutIncludingSentencePunctuation() {
        assertEquals(
            listOf(
                TaskTitleSegment("Open "),
                TaskTitleSegment("https://example.com", "https://example.com"),
                TaskTitleSegment("."),
            ),
            parseTaskTitleLinks("Open https://example.com."),
        )
    }

    @Test
    fun leavesPlainTitleUnchanged() {
        assertEquals(
            listOf(TaskTitleSegment("Buy milk")),
            parseTaskTitleLinks("Buy milk"),
        )
    }
}
