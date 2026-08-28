package com.akergez.shardkeep.domain.model

/** Заголовок таски, разобранный на текст и вынесенные из него `#теги`. */
data class TaskTitleContent(val text: String, val tags: List<String>)

/**
 * Splits inline `#tags` out of a task title so they can be shown as chips instead of raw text.
 * A `#` only opens a tag at a whitespace boundary, which keeps URL fragments intact, and a
 * digits-only run is not a tag — the same rules Obsidian uses.
 */
fun parseTaskTitleTags(title: String): TaskTitleContent {
    if (!title.contains('#')) return TaskTitleContent(title, emptyList())

    val tags = mutableListOf<String>()
    val text = StringBuilder()
    var cursor = 0
    while (cursor < title.length) {
        val char = title[cursor]
        if (char == '#' && (cursor == 0 || title[cursor - 1].isWhitespace())) {
            var end = cursor + 1
            while (end < title.length && title[end].isTaskTagChar()) end++
            val tag = title.substring(cursor + 1, end)
            if (tag.isNotEmpty() && tag.any { !it.isDigit() }) {
                tags += tag
                // Drop the space that separated the tag so punctuation stays attached to the text.
                if (text.isNotEmpty() && text.last().isWhitespace()) text.deleteAt(text.length - 1)
                cursor = end
                continue
            }
        }
        text.append(char)
        cursor++
    }

    if (tags.isEmpty()) return TaskTitleContent(title, emptyList())
    return TaskTitleContent(
        text = text.toString().trim().replace(WHITESPACE_RUN_REGEX, " "),
        tags = tags.distinct(),
    )
}

private fun Char.isTaskTagChar(): Boolean = isLetterOrDigit() || this == '_' || this == '-' || this == '/'

private val WHITESPACE_RUN_REGEX = Regex("\\s{2,}")

/** Кусок заголовка: обычный текст либо ссылка ([url] непустой). */
data class TaskTitleSegment(val text: String, val url: String? = null)

/**
 * Разбирает заголовок на отрезки текста и ссылок — markdown `[label](url)` и голые `http(s)://`.
 * Хвостовая пунктуация в ссылку не входит: точка после адреса принадлежит предложению.
 */
fun parseTaskTitleLinks(title: String): List<TaskTitleSegment> {
    val segments = mutableListOf<TaskTitleSegment>()
    var cursor = 0
    TASK_TITLE_LINK_REGEX.findAll(title).forEach { match ->
        if (match.range.first > cursor) {
            segments += TaskTitleSegment(title.substring(cursor, match.range.first))
        }

        val markdownLabel = match.groups[1]?.value
        val markdownUrl = match.groups[2]?.value
        if (markdownLabel != null && markdownUrl != null) {
            segments += TaskTitleSegment(markdownLabel, markdownUrl)
        } else {
            val rawUrl = checkNotNull(match.groups[3]?.value)
            val url = rawUrl.trimEnd('.', ',', ';', ':', '!', '?')
            segments += TaskTitleSegment(url, url)
            if (url.length < rawUrl.length) {
                segments += TaskTitleSegment(rawUrl.substring(url.length))
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < title.length) segments += TaskTitleSegment(title.substring(cursor))
    return segments.ifEmpty { listOf(TaskTitleSegment(title)) }
}

private val TASK_TITLE_LINK_REGEX = Regex(
    pattern = """\[([^\]\n]+)]\((https?://[^\s)]+)\)|(https?://[^\s<]+)""",
    option = RegexOption.IGNORE_CASE,
)
