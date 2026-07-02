package com.akergez.shardkeep.notesdirectory.markdown

internal data class MarkdownLine(
    val content: String,
    val ending: String,
)

internal fun tokenizeLines(text: String): List<MarkdownLine> {
    if (text.isEmpty()) return emptyList()
    val result = mutableListOf<MarkdownLine>()
    var start = 0
    var index = 0
    while (index < text.length) {
        when (text[index]) {
            '\r' -> {
                val ending = if (index + 1 < text.length && text[index + 1] == '\n') "\r\n" else "\r"
                result += MarkdownLine(text.substring(start, index), ending)
                index += ending.length
                start = index
            }
            '\n' -> {
                result += MarkdownLine(text.substring(start, index), "\n")
                index += 1
                start = index
            }
            else -> index += 1
        }
    }
    if (start < text.length) result += MarkdownLine(text.substring(start), "")
    return result
}

internal fun List<MarkdownLine>.render(): String = joinToString("") { it.content + it.ending }

internal fun List<MarkdownLine>.preferredEnding(): String =
    firstOrNull { it.ending.isNotEmpty() }?.ending ?: "\n"
