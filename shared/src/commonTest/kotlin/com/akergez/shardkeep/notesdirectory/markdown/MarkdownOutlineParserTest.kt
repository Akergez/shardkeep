package com.akergez.shardkeep.notesdirectory.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownOutlineParserTest {
    @Test
    fun extractsHeadingsAndIgnoresMetadataAndCode() {
        val markdown = """
            ---
            title: "# not heading"
            ---
            # Project
            ```markdown
            ## Example
            ```
            ## Tasks
        """.trimIndent()

        assertEquals(
            listOf(MarkdownHeading(1, "Project"), MarkdownHeading(2, "Tasks")),
            MarkdownOutlineParser().parse(markdown),
        )
    }
}
