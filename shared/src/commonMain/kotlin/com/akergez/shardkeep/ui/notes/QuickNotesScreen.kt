package com.akergez.shardkeep.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akergez.shardkeep.domain.model.QuickNote
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.delete
import shardkeep.shared.generated.resources.ic_delete
import shardkeep.shared.generated.resources.no_notes
import shardkeep.shared.generated.resources.no_notes_hint

/** Список быстрых заметок: текстовые превью и первые пункты чек-листов. */
@Composable
fun QuickNotesScreen(
    viewModel: QuickNotesViewModel,
    onError: () -> Unit,
    onRequestDelete: (QuickNote) -> Unit,
    onNoteClick: (QuickNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    if (notes.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.no_notes), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.no_notes_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
        items(notes, key = { it.id.path }) { note ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNoteClick(note) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRequestDelete(note) }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_delete),
                            contentDescription = stringResource(Res.string.delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when (note) {
                    is QuickNote.Text -> Text(
                        note.content,
                        maxLines = 3,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is QuickNote.Checklist -> ChecklistPreview(
                        note = note,
                        onToggle = { index, checked ->
                            viewModel.setChecklistItem(note.id, index, checked, onError)
                        },
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

private const val CHECKLIST_PREVIEW_ITEMS = 4

@Composable
private fun ChecklistPreview(
    note: QuickNote.Checklist,
    onToggle: (index: Int, checked: Boolean) -> Unit,
) {
    Column {
        note.items.take(CHECKLIST_PREVIEW_ITEMS).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = { checked -> onToggle(item.index, checked) },
                )
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (note.items.size > CHECKLIST_PREVIEW_ITEMS) {
            Text(
                "+ ${note.items.size - CHECKLIST_PREVIEW_ITEMS}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
