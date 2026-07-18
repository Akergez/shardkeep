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
import java.io.RandomAccessFile

class DesktopNotesDirectoryStorage(private val root: File) : NotesDirectoryStorage {
    private val changes = MutableSharedFlow<NotesDirectoryChange>(extraBufferCapacity = 64)

    override suspend fun listMarkdownFiles() = withContext(Dispatchers.IO) {
        root.walkTopDown().filter { it.isFile && it.extension.equals("md", true) }
            .map(::metadata).toList()
    }

    override suspend fun listAllFiles(isIgnored: (String) -> Boolean) = withContext(Dispatchers.IO) {
        root.walkTopDown().onEnter { dir ->
            val path = relative(dir)
            path.isEmpty() || (path != ".tresse" && !isIgnored(path))
        }.filter { file ->
            file.isFile && relative(file).let { it != ".tresse" && !it.startsWith(".tresse/") && !isIgnored(it) }
        }.map(::metadata).toList()
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

    override suspend fun delete(path: NotesDirectoryPath) = withContext(Dispatchers.IO) {
        val file = resolve(path.value)
        if (!file.exists()) throw NotesDirectoryException.NotFound(path)
        if (!file.delete()) throw NotesDirectoryException.AccessDenied()
        changes.tryEmit(NotesDirectoryChange.Deleted(path))
        Unit
    }

    override fun observeChanges(): Flow<NotesDirectoryChange> = changes
    override suspend fun fileSize(path: NotesDirectoryPath): Long = withContext(Dispatchers.IO) { resolve(path.value).length() }
    override suspend fun readRange(path: NotesDirectoryPath, offset: Long, len: Long) = withContext(Dispatchers.IO) {
        if (len <= 0) return@withContext ByteArray(0)
        RandomAccessFile(resolve(path.value), "r").use { input ->
            if (offset >= input.length()) return@use ByteArray(0)
            input.seek(offset)
            ByteArray(minOf(len, input.length() - offset, Int.MAX_VALUE.toLong()).toInt()).also { input.readFully(it) }
        }
    }
    override suspend fun writeBinary(path: NotesDirectoryPath, data: ByteArray) = withContext(Dispatchers.IO) {
        val file = resolve(path.value)
        file.parentFile?.mkdirs()
        val existed = file.exists()
        file.writeBytes(data)
        changes.tryEmit(if (existed) NotesDirectoryChange.Modified(path) else NotesDirectoryChange.Created(path))
        version(file)
    }
    override suspend fun storeList(dir: String) = withContext(Dispatchers.IO) {
        resolve(dir).listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
    }
    override suspend fun storeRead(path: String) = withContext(Dispatchers.IO) { resolve(path).takeIf { it.isFile }?.readBytes() }
    override suspend fun storeWrite(path: String, data: ByteArray) = withContext(Dispatchers.IO) {
        resolve(path).also { it.parentFile?.mkdirs() }.writeBytes(data)
    }
    override suspend fun storeDelete(path: String) = withContext(Dispatchers.IO) { resolve(path).delete(); Unit }

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
