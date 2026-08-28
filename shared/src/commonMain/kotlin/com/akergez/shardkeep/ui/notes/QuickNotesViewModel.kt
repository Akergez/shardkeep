package com.akergez.shardkeep.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.model.QuickNoteId
import com.akergez.shardkeep.domain.repository.QuickNoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Лента быстрых заметок.
 *
 * Репозиторий приходит снаружи и может смениться (другой воркспейс), поэтому он
 * живёт во флоу: подписка на заметки переезжает на новый источник сама.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickNotesViewModel : ViewModel() {
    private val repository = MutableStateFlow<QuickNoteRepository?>(null)

    val notes: StateFlow<List<QuickNote>> = repository
        .flatMapLatest { it?.observeNotes() ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun bind(repository: QuickNoteRepository) {
        this.repository.value = repository
    }

    fun setChecklistItem(noteId: QuickNoteId, itemIndex: Int, isChecked: Boolean, onError: () -> Unit) {
        val repo = repository.value ?: return
        viewModelScope.launch {
            runCatching { repo.setChecklistItem(noteId, itemIndex, isChecked) }.onFailure { onError() }
        }
    }
}
