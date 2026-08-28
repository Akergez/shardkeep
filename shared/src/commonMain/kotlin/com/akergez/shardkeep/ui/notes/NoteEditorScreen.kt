package com.akergez.shardkeep.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.domain.repository.QuickNoteRepository
import com.akergez.shardkeep.ui.UiMessage
import com.akergez.shardkeep.ui.component.CircleCheckbox
import com.akergez.shardkeep.ui.text
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.add_item
import shardkeep.shared.generated.resources.delete
import shardkeep.shared.generated.resources.ic_add
import shardkeep.shared.generated.resources.ic_arrow_back
import shardkeep.shared.generated.resources.ic_delete
import shardkeep.shared.generated.resources.new_note
import shardkeep.shared.generated.resources.note_content
import shardkeep.shared.generated.resources.note_title
import shardkeep.shared.generated.resources.save

/**
 * Редактор заметки — отдельный экран поверх оболочки.
 *
 * Со своим снекбаром: оболочка в это время не отрисована, и её хосту сообщение показать негде.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    target: NoteEditorTarget,
    repository: QuickNoteRepository,
    onBack: () -> Unit,
) {
    val form = rememberNoteEditorFormState(target)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = UiMessage.NoteSaveFailed.text()

    val newItemFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var focusNewItem by remember { mutableStateOf(false) }

    LaunchedEffect(form.items.size, focusNewItem) {
        if (focusNewItem && form.items.isNotEmpty()) {
            newItemFocusRequester.requestFocus()
            keyboardController?.show()
            focusNewItem = false
        }
    }

    fun addChecklistItem() {
        form.addItem()
        focusNewItem = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = null)
                    }
                },
                title = { Text(form.editedNoteTitle ?: stringResource(Res.string.new_note)) },
                actions = {
                    TextButton(
                        enabled = form.canSave,
                        onClick = {
                            scope.launch {
                                form.save(repository)
                                    .onSuccess { onBack() }
                                    .onFailure { snackbarHostState.showSnackbar(saveFailedMessage) }
                            }
                        },
                    ) { Text(stringResource(Res.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { form.title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.note_title)) },
                singleLine = true,
            )

            if (!form.isChecklist) {
                OutlinedTextField(
                    value = form.content,
                    onValueChange = { form.content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.note_content)) },
                    minLines = 8,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                form.items.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleCheckbox(
                            checked = item.isChecked,
                            onCheckedChange = { checked ->
                                form.updateItem(index, item.copy(isChecked = checked))
                            },
                        )
                        TextField(
                            value = item.text,
                            onValueChange = { text -> form.updateItem(index, item.copy(text = text)) },
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (index == form.items.lastIndex) {
                                        Modifier.focusRequester(newItemFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { addChecklistItem() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        )
                        IconButton(onClick = { form.removeItem(index) }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_delete),
                                contentDescription = stringResource(Res.string.delete),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
                }
                TextButton(
                    onClick = { addChecklistItem() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.add_item))
                }
            }
        }
    }
}
