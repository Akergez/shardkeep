package com.akergez.shardkeep.notesdirectory.markdown

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.NotesDirectoryLocation
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskFormat
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskPriority
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.model.TaskTimeSpan
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
            val dueDate = extractDate(body, EMOJI_DUE_REGEX, DATAVIEW_DUE_REGEX)
            val scheduledDate = extractDate(body, EMOJI_SCHEDULED_REGEX, DATAVIEW_SCHEDULED_REGEX)
            val startDate = extractDate(body, EMOJI_START_REGEX, DATAVIEW_START_REGEX)
            val endDate = extractDate(body, EMOJI_END_REGEX, DATAVIEW_END_REGEX)
            val time = extractTime(body)
            val recurrence = extractRecurrence(body)
            val priority = extractPriority(body)
            val title = cleanTitle(body)
            val status = if (marker.equals("x", ignoreCase = true)) TaskStatus.Done else TaskStatus.Todo

            tasks += Task(
                id = TaskId("${path.value}:$index:${title.hashCode()}"),
                title = title,
                status = status,
                dueDate = dueDate,
                scheduledDate = scheduledDate,
                startDate = startDate,
                endDate = endDate,
                time = time,
                recurrence = recurrence,
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

    private fun extractDate(body: String, emoji: Regex, dataview: Regex): CalendarDate? {
        val regex = when (format) {
            TaskFormat.Emoji -> emoji
            TaskFormat.Dataview -> dataview
        }
        val groups = regex.find(body)?.groupValues ?: return null
        return runCatching {
            CalendarDate(groups[1].toInt(), groups[2].toInt(), groups[3].toInt())
        }.getOrNull()
    }

    private fun extractTime(body: String): TaskTimeSpan? {
        val raw = when (format) {
            TaskFormat.Emoji -> EMOJI_TIME_REGEX.find(body)?.groupValues?.get(1)
            TaskFormat.Dataview -> DATAVIEW_TIME_REGEX.find(body)?.groupValues?.get(1)
        } ?: return null
        return TaskTimeSpan.parse(raw)
    }

    private fun extractRecurrence(body: String): String? {
        val raw = when (format) {
            TaskFormat.Emoji -> EMOJI_RECURRENCE_REGEX.find(body)?.groupValues?.get(1)
            TaskFormat.Dataview -> DATAVIEW_RECURRENCE_REGEX.find(body)?.groupValues?.get(1)
        } ?: return null
        return raw.trim().ifEmpty { null }
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
            .replace(EMOJI_RECURRENCE_REGEX, "")
            .replace(EMOJI_DUE_REGEX, "")
            .replace(EMOJI_SCHEDULED_REGEX, "")
            .replace(EMOJI_START_REGEX, "")
            .replace(EMOJI_END_REGEX, "")
            .replace(EMOJI_TIME_REGEX, "")
            .replace(EMOJI_PRIORITY_HIGH, "")
            .replace(EMOJI_PRIORITY_LOW, "")
            .collapseSpaces()
        TaskFormat.Dataview -> body
            .replace(DATAVIEW_ANY_FIELD_REGEX, "")
            .collapseSpaces()
    }

    /**
     * Поле, вырезанное из середины строки, оставляет за собой два пробела подряд —
     * «Созвон ⏰ 14:00 в переговорке» иначе стал бы «Созвон  в переговорке» и в
     * таком виде уехал бы обратно в файл при первой же правке.
     */
    private fun String.collapseSpaces(): String = replace(REPEATED_SPACES, " ").trim()

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

        /**
         * Все эмодзи-поля, какие может поставить Tasks, включая те, что мы не читаем
         * (создано, выполнено, отменено, id, зависимости). Нужны как ограничитель:
         * текст 🔁 свободный и тянется до следующего поля, а не до конца строки.
         */
        const val FIELD_EMOJI = "📅|📆|🗓|⏳|⌛|🛫|🏁|⏰|🔁|➕|✅|❌|🆔|⛔|🔺|⏫|🔼|🔽|⏬"

        val EMOJI_DUE_REGEX = Regex("📅\\s*(\\d{4})-(\\d{2})-(\\d{2})")
        val EMOJI_SCHEDULED_REGEX = Regex("⏳\\s*(\\d{4})-(\\d{2})-(\\d{2})")
        val EMOJI_START_REGEX = Regex("🛫\\s*(\\d{4})-(\\d{2})-(\\d{2})")
        val EMOJI_END_REGEX = Regex("🏁\\s*(\\d{4})-(\\d{2})-(\\d{2})")

        /**
         * `⏰ 10:00-18:00`, `⏰ 10:00`, `⏰ -18:00`. Границы перечислены явно, а не
         * «до следующего поля»: за временем на строке обычно идёт ещё текст заголовка,
         * и жадный вариант утащил бы его в атрибут, а потом вырезал из названия.
         */
        val EMOJI_TIME_REGEX = Regex(
            "⏰\\s*(\\d{1,2}[:.]\\d{2}\\s*[-–—]\\s*\\d{1,2}[:.]\\d{2}" +
                "|[-–—]\\s*\\d{1,2}[:.]\\d{2}" +
                "|\\d{1,2}[:.]\\d{2})",
        )

        /** Текст правила тянется до следующего поля или до конца строки — как в Tasks. */
        val EMOJI_RECURRENCE_REGEX = Regex("🔁\\s*(.*?)(?=\\s*(?:$FIELD_EMOJI)|\\s*$)")

        val EMOJI_PRIORITY_HIGH = Regex("[🔼⏫🔺]")
        val EMOJI_PRIORITY_LOW = Regex("[🔽⏬]")

        // Dataview format regexes (matches both [field:: value] and (field:: value))
        val DATAVIEW_DUE_REGEX = Regex("""[\[(]due::\s*(\d{4})-(\d{2})-(\d{2})[\])]""")
        val DATAVIEW_SCHEDULED_REGEX = Regex("""[\[(]scheduled::\s*(\d{4})-(\d{2})-(\d{2})[\])]""")
        val DATAVIEW_START_REGEX = Regex("""[\[(]start::\s*(\d{4})-(\d{2})-(\d{2})[\])]""")
        val DATAVIEW_END_REGEX = Regex("""[\[(]end::\s*(\d{4})-(\d{2})-(\d{2})[\])]""")
        val DATAVIEW_TIME_REGEX = Regex("""[\[(]time::\s*([^\])]+)[\])]""")
        val DATAVIEW_RECURRENCE_REGEX = Regex("""[\[(]repeat::\s*([^\])]+)[\])]""")
        val DATAVIEW_PRIORITY_REGEX = Regex("""[\[(]priority::\s*(\w+)[\])]""")
        val DATAVIEW_ANY_FIELD_REGEX = Regex("""[\[(][a-zA-Z]+::\s*[^\]\)]+[\])]""")
        val REPEATED_SPACES = Regex("[ \\t]{2,}")
        val SHARDKEEP_TYPE_REGEX = Regex("shardkeep-type:\\s*(quick-note|checklist)")
    }
}
