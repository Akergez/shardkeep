package com.akergez.shardkeep.notesdirectory.markdown

import com.akergez.shardkeep.domain.model.ChecklistItem
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.model.QuickNoteId
import com.akergez.shardkeep.notesdirectory.NotesDirectoryException
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath

class QuickNoteMarkdownCodec {
    fun decode(path: NotesDirectoryPath, markdown: String): QuickNote? {
        val type = FRONTMATTER_TYPE.find(markdown)?.groupValues?.get(1) ?: return null
        val allLines = tokenizeLines(markdown)
        val relativeEnd = allLines.drop(1).indexOfFirst { it.content.trim() == "---" }
        val frontmatterEnd = if (relativeEnd < 0) -1 else relativeEnd + 1
        if (frontmatterEnd < 0) return null
        val lines = allLines.drop(frontmatterEnd + 1).dropWhile { it.content.isBlank() }
        val title = lines.firstOrNull()?.content?.let { TITLE.matchEntire(it)?.groupValues?.get(1)?.trim() }
            ?: path.value.substringAfterLast('/').substringBeforeLast('.')
        return when (type) {
            TYPE_NOTE -> QuickNote.Text(
                id = QuickNoteId(path.value),
                title = title,
                content = lines.drop(if (lines.firstOrNull()?.content?.startsWith("# ") == true) 1 else 0)
                    .joinToString("\n") { it.content }
                    .trim(),
            )
            TYPE_CHECKLIST -> QuickNote.Checklist(
                id = QuickNoteId(path.value),
                title = title,
                items = lines.mapNotNull { line -> CHECKBOX.matchEntire(line.content) }
                    .mapIndexed { index, match ->
                        ChecklistItem(
                            index = index,
                            text = match.groupValues[2].trim(),
                            isChecked = match.groupValues[1].equals("x", ignoreCase = true),
                        )
                    },
            )
            else -> null
        }
    }

    fun encodeText(title: String, content: String): String =
        "---\nshardkeep-type: $TYPE_NOTE\n---\n# ${title.trim()}\n\n${content.trim()}"

    fun encodeChecklist(title: String, items: List<String>): String = buildString {
        append("---\nshardkeep-type: $TYPE_CHECKLIST\n---\n# ").append(title.trim())
        items.filter { it.isNotBlank() }.forEach { append("\n- [ ] ").append(it.trim()) }
    }

    fun encodeChecklistFull(title: String, items: List<ChecklistItem>): String = buildString {
        append("---\nshardkeep-type: $TYPE_CHECKLIST\n---\n# ").append(title.trim())
        items.filter { it.text.isNotBlank() }.forEach { item ->
            append("\n- [").append(if (item.isChecked) "x" else " ").append("] ").append(item.text.trim())
        }
    }

    fun setChecklistItem(markdown: String, path: NotesDirectoryPath, itemIndex: Int, checked: Boolean): String {
        val lines = tokenizeLines(markdown).toMutableList()
        var currentIndex = 0
        lines.forEachIndexed { lineIndex, line ->
            val match = CHECKBOX.matchEntire(line.content) ?: return@forEachIndexed
            if (currentIndex == itemIndex) {
                val bracketIdx = line.content.indexOf('[')
                if (bracketIdx < 0) throw NotesDirectoryException.InvalidDocument("Checklist marker is missing")
                val markerRange = (bracketIdx + 1)..(bracketIdx + 1)
                lines[lineIndex] = line.copy(
                    content = line.content.replaceRange(markerRange, if (checked) "x" else " "),
                )
                return lines.render()
            }
            currentIndex += 1
        }
        throw NotesDirectoryException.InvalidDocument("Checklist item $itemIndex was not found in ${path.value}")
    }

    companion object {
        const val TYPE_NOTE = "quick-note"
        const val TYPE_CHECKLIST = "checklist"
        private val FRONTMATTER_TYPE = Regex(
            "(?m)^shardkeep-type:\\s*(quick-note|checklist)\\s*$",
        )
        private val TITLE = Regex("^#\\s+(.+?)\\s*$")
        private val CHECKBOX = Regex("^\\s*[-*+]\\s+\\[([ xX])]\\s+(.*)$")
    }
}
