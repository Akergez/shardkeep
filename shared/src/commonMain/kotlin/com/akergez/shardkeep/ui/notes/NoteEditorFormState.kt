package com.akergez.shardkeep.ui.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.akergez.shardkeep.domain.model.ChecklistItem
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.repository.QuickNoteRepository

/**
 * Состояние редактора заметки: поля формы и правило «можно ли сохранять».
 *
 * Знает, чем именно является заметка (текст или чек-лист), и куда её записывать,
 * чтобы экран отвечал только за вёрстку.
 */
@Stable
class NoteEditorFormState(private val target: NoteEditorTarget) {
    val isChecklist: Boolean = target == NoteEditorTarget.NewChecklist ||
        (target is NoteEditorTarget.Edit && target.note is QuickNote.Checklist)

    /** Заголовок экрана: у правки — имя заметки, у новой его ещё нет. */
    val editedNoteTitle: String? = (target as? NoteEditorTarget.Edit)?.note?.title

    var title by mutableStateOf(editedNoteTitle.orEmpty())
    var content by mutableStateOf(
        ((target as? NoteEditorTarget.Edit)?.note as? QuickNote.Text)?.content.orEmpty(),
    )
    var saving by mutableStateOf(false)
        private set

    val items = mutableStateListOf<ChecklistItem>().also { list ->
        ((target as? NoteEditorTarget.Edit)?.note as? QuickNote.Checklist)?.let { list.addAll(it.items) }
    }

    val canSave: Boolean
        get() = !saving && title.isNotBlank() &&
            if (isChecklist) items.any { it.text.isNotBlank() } else content.isNotBlank()

    fun addItem() {
        val nextIndex = (items.maxOfOrNull { it.index } ?: -1) + 1
        items.add(ChecklistItem(index = nextIndex, text = "", isChecked = false))
    }

    fun updateItem(index: Int, item: ChecklistItem) {
        items[index] = item
    }

    fun removeItem(index: Int) {
        items.removeAt(index)
    }

    /** Пишет заметку в репозиторий; на неудаче снимает флаг сохранения, чтобы можно было повторить. */
    suspend fun save(repository: QuickNoteRepository): Result<Unit> {
        saving = true
        val filledItems = items.filter { it.text.isNotBlank() }
        return runCatching {
            when (target) {
                NoteEditorTarget.NewText -> repository.createTextNote(title.trim(), content.trim())
                NoteEditorTarget.NewChecklist ->
                    repository.createChecklist(title.trim(), filledItems.map { it.text.trim() })
                is NoteEditorTarget.Edit -> when (val note = target.note) {
                    is QuickNote.Text -> repository.updateTextNote(note.id, title.trim(), content.trim())
                    is QuickNote.Checklist -> repository.updateChecklist(note.id, title.trim(), filledItems)
                }
            }
        }.onFailure { saving = false }
    }
}

@Composable
fun rememberNoteEditorFormState(target: NoteEditorTarget): NoteEditorFormState =
    remember(target) { NoteEditorFormState(target) }
