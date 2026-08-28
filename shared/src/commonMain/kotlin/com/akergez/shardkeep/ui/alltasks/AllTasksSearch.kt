package com.akergez.shardkeep.ui.alltasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.domain.model.parseTaskTitleTags
import com.akergez.shardkeep.domain.usecase.TaskSearchResult
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.clear
import shardkeep.shared.generated.resources.ic_article
import shardkeep.shared.generated.resources.ic_close
import shardkeep.shared.generated.resources.ic_format_list
import shardkeep.shared.generated.resources.ic_search
import shardkeep.shared.generated.resources.search_nothing_found
import shardkeep.shared.generated.resources.search_tasks

/**
 * Строка поиска над вкладками. Фокус запрашивает вызывающий через [focusRequester]:
 * решение «сразу печатать» принадлежит экрану, а не полю.
 */
@Composable
fun AllTasksSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        placeholder = { Text(stringResource(Res.string.search_tasks)) },
        leadingIcon = {
            Icon(
                painter = painterResource(Res.drawable.ic_search),
                contentDescription = stringResource(Res.string.search_tasks),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = stringResource(Res.string.clear),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
    )
}

/** Находки поиска: файлы и задачи одним списком, нажатие открывает нужную вкладку. */
@Composable
fun AllTasksSearchResults(
    results: List<TaskSearchResult>,
    onSelect: (TaskSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(Res.string.search_nothing_found),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(results, key = ::searchResultKey) { result ->
            SearchResultRow(result = result, onClick = { onSelect(result) })
            HorizontalDivider()
        }
    }
}

/** Файл и задача могут делить путь, поэтому ключ различает и вид находки. */
private fun searchResultKey(result: TaskSearchResult): String = when (result) {
    is TaskSearchResult.File -> "file:${result.path}"
    is TaskSearchResult.Match -> "task:${result.task.id.value}"
}

@Composable
private fun SearchResultRow(result: TaskSearchResult, onClick: () -> Unit) {
    val icon = when (result) {
        is TaskSearchResult.File -> Res.drawable.ic_article
        is TaskSearchResult.Match -> Res.drawable.ic_format_list
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        when (result) {
            is TaskSearchResult.File -> Text(
                text = result.path,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            is TaskSearchResult.Match -> Column {
                // Теги в находке только шумят: в строке задачи они и так станут чипами.
                val title = remember(result.task.title) {
                    parseTaskTitleTags(result.task.title).text
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = result.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
