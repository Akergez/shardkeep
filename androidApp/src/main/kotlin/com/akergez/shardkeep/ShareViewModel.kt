package com.akergez.shardkeep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareUiState(
    val title: String,
    val text: String,
    val imageCount: Int,
    val workspaces: List<WorkspaceRecord>,
    val selectedWorkspaceId: String?,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean
        get() = !saving &&
            selectedWorkspaceId != null &&
            title.isNotBlank() &&
            (text.isNotBlank() || imageCount > 0)
}

class ShareViewModel(
    private val payload: SharePayload,
    workspaces: List<WorkspaceRecord>,
    activeWorkspaceId: String?,
    private val saver: ShareNoteSaver,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ShareUiState(
            title = payload.title,
            text = payload.text,
            imageCount = payload.imageUris.size,
            workspaces = workspaces,
            selectedWorkspaceId = workspaces
                .firstOrNull { it.id == activeWorkspaceId }
                ?.id
                ?: workspaces.firstOrNull()?.id,
        )
    )
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    fun onTitleChange(title: String) = _state.update {
        it.copy(title = title, saveFailed = false)
    }

    fun onTextChange(text: String) = _state.update {
        it.copy(text = text, saveFailed = false)
    }

    fun onWorkspaceSelected(workspaceId: String) = _state.update { current ->
        if (current.workspaces.none { it.id == workspaceId }) current
        else current.copy(selectedWorkspaceId = workspaceId, saveFailed = false)
    }

    fun save() {
        val snapshot = _state.value
        if (!snapshot.canSave) return
        val workspace = snapshot.workspaces.firstOrNull { it.id == snapshot.selectedWorkspaceId } ?: return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            runCatching {
                saver.save(
                    workspace = workspace,
                    title = snapshot.title.trim(),
                    text = snapshot.text.trim(),
                    imageUris = payload.imageUris,
                )
            }.onSuccess {
                _state.update { it.copy(saving = false, saved = true) }
            }.onFailure {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    companion object {
        fun factory(
            payload: SharePayload,
            workspaces: List<WorkspaceRecord>,
            activeWorkspaceId: String?,
            saver: ShareNoteSaver,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShareViewModel(payload, workspaces, activeWorkspaceId, saver) as T
        }
    }
}
