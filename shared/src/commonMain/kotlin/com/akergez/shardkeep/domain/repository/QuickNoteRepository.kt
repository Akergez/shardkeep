package com.akergez.shardkeep.domain.repository

import com.akergez.shardkeep.domain.model.ChecklistItem
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.model.QuickNoteId
import kotlinx.coroutines.flow.Flow

interface QuickNoteRepository {
    fun observeNotes(): Flow<List<QuickNote>>

    suspend fun refresh()

    suspend fun createTextNote(title: String, content: String)

    suspend fun createChecklist(title: String, items: List<String>)

    suspend fun updateTextNote(noteId: QuickNoteId, title: String, content: String) = Unit

    suspend fun updateChecklist(noteId: QuickNoteId, title: String, items: List<ChecklistItem>) = Unit

    suspend fun setChecklistItem(noteId: QuickNoteId, itemIndex: Int, isChecked: Boolean)

    suspend fun delete(noteId: QuickNoteId)
}
