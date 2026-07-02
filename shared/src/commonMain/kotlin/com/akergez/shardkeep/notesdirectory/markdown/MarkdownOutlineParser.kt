package com.akergez.shardkeep.notesdirectory.markdown

data class MarkdownHeading(
    val level: Int,
    val title: String,
)

class MarkdownOutlineParser {
    fun parse(markdown: String): List<MarkdownHeading> {
        val lines = tokenizeLines(markdown)
        val result = mutableListOf<MarkdownHeading>()
        var inFrontmatter = lines.firstOrNull()?.content?.trim() == "---"
        var fenceMarker: String? = null

        lines.forEachIndexed { index, line ->
            val trimmed = line.content.trimStart()
            if (inFrontmatter) {
                if (index > 0 && trimmed == "---") inFrontmatter = false
                return@forEachIndexed
            }
            val marker = when {
                trimmed.startsWith("```") -> "```"
                trimmed.startsWith("~~~") -> "~~~"
                else -> null
            }
            if (marker != null) {
                fenceMarker = if (fenceMarker == null) marker else if (fenceMarker == marker) null else fenceMarker
                return@forEachIndexed
            }
            if (fenceMarker != null) return@forEachIndexed

            HEADING_REGEX.matchEntire(trimmed)?.let { match ->
                result += MarkdownHeading(
                    level = match.groupValues[1].length,
                    title = match.groupValues[2].trim(),
                )
            }
        }
        return result
    }

    private companion object {
        val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+?)\\s*#*\\s*$")
    }
}
