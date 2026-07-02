package com.akergez.shardkeep.domain.model

data class QuickNoteId(val path: String)

sealed interface QuickNote {
    val id: QuickNoteId
    val title: String

    data class Text(
        override val id: QuickNoteId,
        override val title: String,
        val content: String,
    ) : QuickNote

    data class Checklist(
        override val id: QuickNoteId,
        override val title: String,
        val items: List<ChecklistItem>,
    ) : QuickNote
}

data class ChecklistItem(
    val index: Int,
    val text: String,
    val isChecked: Boolean,
)
