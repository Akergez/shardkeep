package com.akergez.shardkeep.notesdirectory.markdown

import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.notesdirectory.NotesDirectoryException
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath

class MarkdownTaskPatcher {
    fun setStatus(markdown: String, task: Task, status: TaskStatus): String {
        val lineIndex = task.location.lineIndex
            ?: throw NotesDirectoryException.InvalidDocument("Task has no source line")
        val expectedLine = task.location.sourceLine
            ?: throw NotesDirectoryException.InvalidDocument("Task has no original source text")
        val lines = tokenizeLines(markdown).toMutableList()
        val actualLine = lines.getOrNull(lineIndex)?.content
            ?: throw NotesDirectoryException.Conflict(NotesDirectoryPath(task.location.path))
        if (actualLine != expectedLine) {
            throw NotesDirectoryException.Conflict(NotesDirectoryPath(task.location.path))
        }
        if (!TASK_REGEX.matches(actualLine)) {
            throw NotesDirectoryException.InvalidDocument("Source line is no longer a task")
        }
        val matchStart = STATUS_REGEX.find(actualLine)?.range?.first
            ?: throw NotesDirectoryException.InvalidDocument("Task status marker is missing")
        val markerRange = (matchStart + 1)..(matchStart + 1)
        val marker = if (status == TaskStatus.Done) "x" else " "
        lines[lineIndex] = lines[lineIndex].copy(
            content = actualLine.replaceRange(markerRange, marker),
        )
        return lines.render()
    }

    fun replaceLine(markdown: String, task: Task, newContent: String): String {
        val lineIndex = task.location.lineIndex
            ?: throw NotesDirectoryException.InvalidDocument("Task has no source line")
        val expectedLine = task.location.sourceLine
            ?: throw NotesDirectoryException.InvalidDocument("Task has no original source text")
        val lines = tokenizeLines(markdown).toMutableList()
        val actualLine = lines.getOrNull(lineIndex)?.content
            ?: throw NotesDirectoryException.Conflict(NotesDirectoryPath(task.location.path))
        if (actualLine != expectedLine) {
            throw NotesDirectoryException.Conflict(NotesDirectoryPath(task.location.path))
        }
        lines[lineIndex] = lines[lineIndex].copy(content = newContent)
        return lines.render()
    }

    fun insertTask(markdown: String, heading: String?, taskLine: String): String {
        require(TASK_REGEX.matches(taskLine)) { "taskLine must be a Markdown checkbox item" }
        val lines = tokenizeLines(markdown).toMutableList()
        val ending = lines.preferredEnding()
        val insertionIndex = if (heading == null) {
            lines.size
        } else {
            findSectionEnd(lines, heading)
                ?: throw NotesDirectoryException.InvalidDocument("Heading not found: $heading")
        }

        if (lines.isNotEmpty() && insertionIndex == lines.size && lines.last().ending.isEmpty()) {
            lines[lines.lastIndex] = lines.last().copy(ending = ending)
        }
        lines.add(insertionIndex, MarkdownLine(taskLine, if (insertionIndex < lines.size) ending else ""))
        return lines.render()
    }

    private fun findSectionEnd(lines: List<MarkdownLine>, requestedHeading: String): Int? {
        var sectionLevel: Int? = null
        lines.forEachIndexed { index, line ->
            val match = HEADING_REGEX.matchEntire(line.content.trimStart()) ?: return@forEachIndexed
            val level = match.groupValues[1].length
            val title = match.groupValues[2].trim()
            if (sectionLevel == null && title == requestedHeading) {
                sectionLevel = level
            } else if (sectionLevel?.let { level <= it } == true) {
                return index
            }
        }
        return if (sectionLevel == null) null else lines.size
    }

    private companion object {
        val TASK_REGEX = Regex("^\\s*(?:[-+*]|\\d+[.)])\\s+\\[[ xX]](?:\\s+.*)?$")
        val STATUS_REGEX = Regex("\\[([ xX])]")
        val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+?)\\s*#*\\s*$")
    }
}
