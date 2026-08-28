package com.akergez.shardkeep.ui.notes

import com.akergez.shardkeep.domain.model.QuickNote

/** Что открыто в редакторе заметок: новая текстовая, новый чек-лист или правка существующей. */
sealed interface NoteEditorTarget {
    data object NewText : NoteEditorTarget
    data object NewChecklist : NoteEditorTarget
    data class Edit(val note: QuickNote) : NoteEditorTarget
}
