package com.akergez.shardkeep.ui.alltasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.repository.TaskDestination
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.usecase.TaskSearchResult
import com.akergez.shardkeep.domain.usecase.searchTasks
import com.akergez.shardkeep.ui.component.EmptyTasks
import kotlinx.coroutines.launch

/**
 * Все невыполненные задачи, по вкладке на файл, с поиском над ними.
 *
 * Открытая вкладка поднята наружу: по ней кнопка «плюс» понимает, в какой файл
 * класть новую задачу, и она же переживает пересборку списка файлов.
 *
 * Поиск, наоборот, живёт здесь: он ничего не меняет в данных, а его состояние
 * должно обнуляться при каждом заходе на экран — чтобы курсор снова стоял в пустом поле.
 */
@Composable
fun AllTasksScreen(
    files: List<TaskDestination>,
    tasksByFile: Map<String, List<Task>>,
    focusedFilePath: String?,
    today: CalendarDate,
    onCheckedChange: (TaskId, Boolean) -> Unit,
    onScheduleToday: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    onFocusedFileChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (files.isEmpty()) {
        LaunchedEffect(Unit) { onFocusedFileChange(null) }
        EmptyTasks(modifier = modifier)
        return
    }
    val pagerState = rememberPagerState(initialPage = 0) { files.size }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    /** Задача, за которой сюда пришли из поиска: к ней прокручивается её вкладка. */
    var selectedTaskId by remember { mutableStateOf<TaskId?>(null) }
    val results = remember(query, files, tasksByFile) { searchTasks(query, files, tasksByFile) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    LaunchedEffect(files, focusedFilePath) {
        val preservedPage = files.indexOfFirst { it.path == focusedFilePath }
        if (preservedPage >= 0 && preservedPage != pagerState.currentPage) {
            pagerState.scrollToPage(preservedPage)
        }
    }
    LaunchedEffect(pagerState.currentPage, files) {
        onFocusedFileChange(files.getOrNull(pagerState.currentPage)?.path)
    }

    fun openResult(result: TaskSearchResult) {
        val page = files.indexOfFirst { it.path == result.path }
        if (page < 0) return
        query = ""
        selectedTaskId = (result as? TaskSearchResult.Match)?.task?.id
        focusManager.clearFocus()
        // Без анимации: из поиска приходят в другой конец списка вкладок, и пролистывание
        // мимо всех промежуточных ничего не рассказывает.
        scope.launch { pagerState.scrollToPage(page) }
    }

    Column(modifier = modifier) {
        AllTasksSearchField(
            query = query,
            onQueryChange = { query = it },
            onSubmit = { results.firstOrNull()?.let(::openResult) },
            focusRequester = focusRequester,
        )
        if (query.isNotEmpty()) {
            AllTasksSearchResults(results = results, onSelect = ::openResult)
            return@Column
        }
        ScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
            files.forEachIndexed { index, file ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(file.path) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { files[it].path },
        ) { page ->
            val fileTasks = tasksByFile[files[page].path].orEmpty()
            if (fileTasks.isEmpty()) {
                EmptyTasks(modifier = Modifier.fillMaxSize())
            } else {
                val listState = rememberLazyListState()
                // Найденная задача может лежать далеко вниз по файлу — довозим до неё.
                LaunchedEffect(selectedTaskId, fileTasks) {
                    val target = selectedTaskId ?: return@LaunchedEffect
                    val index = fileTasks.indexOfFirst { it.id == target }
                    if (index >= 0) listState.animateScrollToItem(index)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(fileTasks, key = { it.id.value }) { task ->
                        AllTaskRow(
                            task = task,
                            today = today,
                            highlighted = task.id == selectedTaskId,
                            onCheckedChange = { checked -> onCheckedChange(task.id, checked) },
                            onScheduleToday = { onScheduleToday(task) },
                            onEdit = { onEdit(task) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
