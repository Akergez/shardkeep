package com.akergez.shardkeep

import com.akergez.shardkeep.notesdirectory.FileVersion
import com.akergez.shardkeep.notesdirectory.NotesDirectoryChange
import com.akergez.shardkeep.notesdirectory.NotesDirectoryDocument
import com.akergez.shardkeep.notesdirectory.NotesDirectoryException
import com.akergez.shardkeep.notesdirectory.NotesDirectoryFile
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath
import com.akergez.shardkeep.notesdirectory.NotesDirectoryStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shardkeep's own view of the notes directory: find the markdown, read it, write it.
 * It owes nothing to tresse. The sync has its own vault and its own ignore patterns,
 * and those patterns say what gets carried to a remote — a question this walk never asks.
 */
class DesktopNotesDirectoryStorage(private val root: File) : NotesDirectoryStorage {
    private val changes = MutableSharedFlow<NotesDirectoryChange>(extraBufferCapacity = 64)

    /**
     * Hidden entries are skipped, directories included, and that is the whole rule.
     * Nothing under `.git`, `.obsidian` or `.tresse` is a note, so descending into them
     * only costs time — the more so on Android, where the same walk crosses a process
     * boundary per directory.
     */
    override suspend fun listMarkdownFiles() = withContext(Dispatchers.IO) {
        root.walkTopDown()
            .onEnter { it == root || !it.name.startsWith('.') }
            .filter { it.isFile && !it.name.startsWith('.') && it.extension.equals("md", true) }
            .map(::metadata)
            .toList()
    }

    override suspend fun read(path: NotesDirectoryPath) = withContext(Dispatchers.IO) {
        val file = resolve(path.value)
        if (!file.isFile) throw NotesDirectoryException.NotFound(path)
        NotesDirectoryDocument(path, version(file), file.readText())
    }

    override suspend fun write(path: NotesDirectoryPath, expectedVersion: FileVersion, content: String) =
        withContext(Dispatchers.IO) {
            val file = resolve(path.value)
            if (!file.isFile) throw NotesDirectoryException.NotFound(path)
            if (version(file) != expectedVersion) throw NotesDirectoryException.Conflict(path)
            file.writeText(content)
            version(file).also { changes.tryEmit(NotesDirectoryChange.Modified(path)) }
        }

    override suspend fun create(path: NotesDirectoryPath, content: String) = withContext(Dispatchers.IO) {
        val file = resolve(path.value)
        if (file.exists()) throw NotesDirectoryException.Conflict(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        version(file).also { changes.tryEmit(NotesDirectoryChange.Created(path)) }
    }

    override suspend fun writeBinary(path: NotesDirectoryPath, data: ByteArray) = withContext(Dispatchers.IO) {
        val file = resolve(path.value)
        file.parentFile?.mkdirs()
        val existed = file.exists()
        file.writeBytes(data)
        changes.tryEmit(if (existed) NotesDirectoryChange.Modified(path) else NotesDirectoryChange.Created(path))
        version(file)
    }

    override suspend fun delete(path: NotesDirectoryPath) = withContext(Dispatchers.IO) {
        val file = resolve(path.value)
        if (!file.exists()) throw NotesDirectoryException.NotFound(path)
        if (!file.delete()) throw NotesDirectoryException.AccessDenied()
        changes.tryEmit(NotesDirectoryChange.Deleted(path))
        Unit
    }

    override fun observeChanges(): Flow<NotesDirectoryChange> = changes

    private fun metadata(file: File) = NotesDirectoryFile(
        NotesDirectoryPath(relative(file)), version(file), file.length(), file.lastModified(),
    )

    private fun version(file: File) = FileVersion("${file.lastModified()}:${file.length()}")

    private fun relative(file: File) = file.relativeTo(root).invariantSeparatorsPath

    private fun resolve(path: String): File {
        val file = File(root, path).canonicalFile
        require(file.toPath().startsWith(root.canonicalFile.toPath())) { "Path escapes notes directory" }
        return file
    }
}
