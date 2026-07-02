package com.akergez.shardkeep.notesdirectory.markdown

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.NotesDirectoryLocation
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskFormat
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskPriority
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath

class MarkdownTaskParser(private val format: TaskFormat = TaskFormat.Emoji) {
    fun parse(path: NotesDirectoryPath, markdown: String): List<Task> =
        parseWithHeadings(path, markdown).first

    fun parseWithHeadings(path: NotesDirectoryPath, markdown: String): Pair<List<Task>, List<String>> {
        val lines = tokenizeLines(markdown)
        if (isQuickDocumentLines(lines)) return Pair(emptyList(), emptyList())

        val tasks = mutableListOf<Task>()
        val headings = mutableListOf<String>()
        var heading: String? = null
        var inFrontmatter = lines.firstOrNull()?.content?.trim() == "---"
        var inFence = false
        var fenceMarker: String? = null

        lines.forEachIndexed { index, line ->
            val trimmed = line.content.trimStart()

            if (inFrontmatter) {
                if (index > 0 && trimmed == "---") inFrontmatter = false
                return@forEachIndexed
            }

            val currentFence = fencePrefix(trimmed)
            if (currentFence != null) {
                if (!inFence) {
                    inFence = true
                    fenceMarker = currentFence
                } else if (currentFence == fenceMarker) {
                    inFence = false
                    fenceMarker = null
                }
                return@forEachIndexed
            }
            if (inFence) return@forEachIndexed

            HEADING_REGEX.matchEntire(trimmed)?.let { match ->
                val headingTitle = match.groupValues[2].trim()
                heading = headingTitle.ifEmpty { null }
                if (headingTitle.isNotEmpty()) headings += headingTitle
                return@forEachIndexed
            }

            val match = TASK_REGEX.matchEntire(line.content) ?: return@forEachIndexed
            val marker = match.groupValues[2]
            val body = match.groupValues[3].trim()
            val dueDate = extractDueDate(body)
            val scheduledDate = extractScheduledDate(body)
            val priority = extractPriority(body)
            val title = cleanTitle(body)
            val status = if (marker.equals("x", ignoreCase = true)) TaskStatus.Done else TaskStatus.Todo

            tasks += Task(
                id = TaskId("${path.value}:$index:${title.hashCode()}"),
                title = title,
                status = status,
                dueDate = dueDate,
                scheduledDate = scheduledDate,
                priority = priority,
                location = NotesDirectoryLocation(
                    path = path.value,
                    heading = heading,
                    lineIndex = index,
                    sourceLine = line.content,
                ),
            )
        }
        return Pair(tasks, headings)
    }

    private fun extractDueDate(body: String): CalendarDate? = when (format) {
        TaskFormat.Emoji -> EMOJI_DUE_REGEX.find(body)?.groupValues?.let { g ->
            runCatching { CalendarDate(g[1].toInt(), g[2].toInt(), g[3].toInt()) }.getOrNull()
        }
        TaskFormat.Dataview -> DATAVIEW_DUE_REGEX.find(body)?.groupValues?.let { g ->
            runCatching { CalendarDate(g[1].toInt(), g[2].toInt(), g[3].toInt()) }.getOrNull()
        }
    }

    private fun extractScheduledDate(body: String): CalendarDate? = when (format) {
        TaskFormat.Emoji -> EMOJI_SCHEDULED_REGEX.find(body)?.groupValues?.let { g ->
            runCatching { CalendarDate(g[1].toInt(), g[2].toInt(), g[3].toInt()) }.getOrNull()
        }
        TaskFormat.Dataview -> DATAVIEW_SCHEDULED_REGEX.find(body)?.groupValues?.let { g ->
            runCatching { CalendarDate(g[1].toInt(), g[2].toInt(), g[3].toInt()) }.getOrNull()
        }
    }

    private fun extractPriority(body: String): TaskPriority = when (format) {
        TaskFormat.Emoji -> when {
            EMOJI_PRIORITY_HIGH.containsMatchIn(body) -> TaskPriority.High
            EMOJI_PRIORITY_LOW.containsMatchIn(body) -> TaskPriority.Low
            else -> TaskPriority.Normal
        }
        TaskFormat.Dataview -> DATAVIEW_PRIORITY_REGEX.find(body)?.groupValues?.get(1)?.let { value ->
            when (value.lowercase()) {
                "high", "highest" -> TaskPriority.High
                "low", "lowest" -> TaskPriority.Low
                else -> TaskPriority.Normal
            }
        } ?: TaskPriority.Normal
    }

    private fun cleanTitle(body: String): String = when (format) {
        TaskFormat.Emoji -> body
            .replace(EMOJI_DUE_REGEX, "")
            .replace(EMOJI_SCHEDULED_REGEX, "")
            .replace(EMOJI_PRIORITY_HIGH, "")
            .replace(EMOJI_PRIORITY_LOW, "")
            .trim()
        TaskFormat.Dataview -> body
            .replace(DATAVIEW_ANY_FIELD_REGEX, "")
            .trim()
    }

    private fun fencePrefix(line: String): String? = when {
        line.startsWith("```") -> "```"
        line.startsWith("~~~") -> "~~~"
        else -> null
    }

    private fun isQuickDocumentLines(lines: List<MarkdownLine>): Boolean {
        if (lines.firstOrNull()?.content?.trim() != "---") return false
        return lines.drop(1)
            .takeWhile { it.content.trim() != "---" }
            .any { SHARDKEEP_TYPE_REGEX.matches(it.content.trim()) }
    }

    private companion object {
        val TASK_REGEX = Regex("^(\\s*(?:[-+*]|\\d+[.)])\\s+)\\[([ xX])](?:\\s+(.*))?$")
        val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+?)\\s*#*\\s*$")

        // Emoji format regexes
        val EMOJI_DUE_REGEX = Regex("📅\\s*(\\d{4})-(\\d{2})-(\\d{2})")
        val EMOJI_SCHEDULED_REGEX = Regex("⏳\\s*(\\d{4})-(\\d{2})-(\\d{2})")
        val EMOJI_PRIORITY_HIGH = Regex("[🔼⏫🔺]")
        val EMOJI_PRIORITY_LOW = Regex("[🔽⏬]")

        // Dataview format regexes (matches both [field:: value] and (field:: value))
        val DATAVIEW_DUE_REGEX = Regex("""[\[(]due::\s*(\d{4})-(\d{2})-(\d{2})[\])]""")
        val DATAVIEW_SCHEDULED_REGEX = Regex("""[\[(]scheduled::\s*(\d{4})-(\d{2})-(\d{2})[\])]""")
        val DATAVIEW_PRIORITY_REGEX = Regex("""[\[(]priority::\s*(\w+)[\])]""")
        val DATAVIEW_ANY_FIELD_REGEX = Regex("""[\[(][a-zA-Z]+::\s*[^\]\)]+[\])]""")
        val SHARDKEEP_TYPE_REGEX = Regex("shardkeep-type:\\s*(quick-note|checklist)")
    }
}
