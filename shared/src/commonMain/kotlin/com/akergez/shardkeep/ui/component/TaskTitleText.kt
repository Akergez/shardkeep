package com.akergez.shardkeep.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.model.parseTaskTitleLinks
import com.akergez.shardkeep.domain.model.parseTaskTitleTags

/**
 * Заголовок задачи: теги уже вынесены в чипы, ссылки кликабельны, сделанное зачёркнуто.
 */
@Composable
fun TaskTitleText(task: Task) {
    val linkColor = MaterialTheme.colorScheme.primary
    val done = task.status == TaskStatus.Done
    val linkDecoration = if (done) {
        TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
    } else {
        TextDecoration.Underline
    }
    val title = remember(task.title) { parseTaskTitleTags(task.title).text }
    if (title.isEmpty()) return
    val text = remember(title, linkColor, linkDecoration) {
        buildAnnotatedString {
            parseTaskTitleLinks(title).forEach { segment ->
                val url = segment.url
                if (url == null) {
                    append(segment.text)
                } else {
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = linkDecoration,
                                ),
                            ),
                        ),
                    ) {
                        append(segment.text)
                    }
                }
            }
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
    )
}
