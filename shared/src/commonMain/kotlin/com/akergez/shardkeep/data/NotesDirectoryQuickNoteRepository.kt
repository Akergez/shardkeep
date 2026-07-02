package com.akergez.shardkeep.data

import com.akergez.shardkeep.domain.model.ChecklistItem
import com.akergez.shardkeep.domain.model.QuickNote
import com.akergez.shardkeep.domain.model.QuickNoteId
import com.akergez.shardkeep.domain.repository.QuickNoteRepository
import com.akergez.shardkeep.notesdirectory.NotesDirectoryException
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath
import com.akergez.shardkeep.notesdirectory.NotesDirectoryStorage
import com.akergez.shardkeep.notesdirectory.markdown.QuickNoteMarkdownCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotesDirectoryQuickNoteRepository(
    private val storage: NotesDirectoryStorage,
    private val quickNotesFolder: String = "Shardkeep/Quick Notes",
    private val codec: QuickNoteMarkdownCodec = QuickNoteMarkdownCodec(),
) : QuickNoteRepository {
    private val notes = MutableStateFlow<List<QuickNote>>(emptyList())

    override fun observeNotes(): Flow<List<QuickNote>> = notes.asStateFlow()

    override suspend fun refresh() {
        val refreshed = mutableListOf<QuickNote>()
        storage.listMarkdownFiles()
            .filter { it.path.value.startsWith("$quickNotesFolder/") }
            .forEach { file ->
                val document = storage.read(file.path)
                codec.decode(document.path, document.content)?.let(refreshed::add)
            }
        notes.value = refreshed.sortedBy { it.title }
    }

    override suspend fun createTextNote(title: String, content: String) {
        createUnique(title, codec.encodeText(title, content))
    }

    override suspend fun createChecklist(title: String, items: List<String>) {
        createUnique(title, codec.encodeChecklist(title, items))
    }

    override suspend fun updateTextNote(noteId: QuickNoteId, title: String, content: String) {
        val path = NotesDirectoryPath(noteId.path)
        val document = storage.read(path)
        storage.write(path, document.version, codec.encodeText(title, content))
        refresh()
    }

    override suspend fun updateChecklist(noteId: QuickNoteId, title: String, items: List<ChecklistItem>) {
        val path = NotesDirectoryPath(noteId.path)
        val document = storage.read(path)
        storage.write(path, document.version, codec.encodeChecklistFull(title, items))
        refresh()
    }

    override suspend fun setChecklistItem(noteId: QuickNoteId, itemIndex: Int, isChecked: Boolean) {
        val path = NotesDirectoryPath(noteId.path)
        val document = storage.read(path)
        val changed = codec.setChecklistItem(document.content, path, itemIndex, isChecked)
        storage.write(path, document.version, changed)
        refresh()
    }

    override suspend fun delete(noteId: QuickNoteId) {
        storage.delete(NotesDirectoryPath(noteId.path))
        refresh()
    }

    private suspend fun createUnique(title: String, markdown: String) {
        val baseName = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "-")
            .ifBlank { "Note" }
        var suffix = 1
        while (true) {
            val name = if (suffix == 1) baseName else "$baseName-$suffix"
            val path = NotesDirectoryPath("$quickNotesFolder/$name.md")
            try {
                storage.create(path, markdown)
                refresh()
                return
            } catch (_: NotesDirectoryException.Conflict) {
                suffix += 1
            }
        }
    }

    companion object {
        const val QUICK_NOTES_FOLDER = "Shardkeep/Quick Notes"
    }
}
