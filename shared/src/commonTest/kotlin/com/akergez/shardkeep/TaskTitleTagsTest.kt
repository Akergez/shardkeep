package com.akergez.shardkeep

import com.akergez.shardkeep.domain.model.TaskTitleContent
import com.akergez.shardkeep.domain.model.parseTaskTitleTags
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskTitleTagsTest {
    @Test
    fun extractsTagsAndRemovesThemFromTheTitle() {
        assertEquals(
            TaskTitleContent("Buy milk", listOf("home", "errands")),
            parseTaskTitleTags("Buy #home milk #errands"),
        )
    }

    @Test
    fun keepsNestedAndUnicodeTags() {
        assertEquals(
            TaskTitleContent("Prepare release", listOf("work/urgent", "проект_1")),
            parseTaskTitleTags("#work/urgent Prepare release #проект_1"),
        )
    }

    @Test
    fun leavesTitleUntouchedWhenThereAreNoTags() {
        assertEquals(
            TaskTitleContent("Buy milk", emptyList()),
            parseTaskTitleTags("Buy milk"),
        )
    }

    @Test
    fun ignoresHashesThatAreNotTagBoundaries() {
        assertEquals(
            TaskTitleContent("Read https://example.com/docs#install and issue C#5", emptyList()),
            parseTaskTitleTags("Read https://example.com/docs#install and issue C#5"),
        )
    }

    @Test
    fun ignoresDigitOnlyHashes() {
        assertEquals(
            TaskTitleContent("Fix #123 regression", emptyList()),
            parseTaskTitleTags("Fix #123 regression"),
        )
    }

    @Test
    fun keepsPunctuationThatFollowsATag() {
        assertEquals(
            TaskTitleContent("Call the bank, please.", listOf("home")),
            parseTaskTitleTags("Call the bank #home, please."),
        )
    }

    @Test
    fun deduplicatesRepeatedTags() {
        assertEquals(
            TaskTitleContent("Ship it", listOf("work")),
            parseTaskTitleTags("#work Ship it #work"),
        )
    }
}
