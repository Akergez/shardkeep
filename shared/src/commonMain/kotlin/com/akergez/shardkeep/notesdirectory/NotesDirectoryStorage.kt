package com.akergez.shardkeep.notesdirectory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

data class NotesDirectoryPath(val value: String) {
    init {
        require(value.isNotBlank()) { "Path cannot be blank" }
        require(!value.startsWith('/')) { "Path must be relative" }
        require(value.split('/').none { it == ".." }) { "Path cannot escape the notes directory" }
    }
}

data class FileVersion(val value: String)

data class NotesDirectoryFile(
    val path: NotesDirectoryPath,
    val version: FileVersion,
    val size: Long,
    val modifiedAtMillis: Long = 0,
)

data class NotesDirectoryDocument(
    val path: NotesDirectoryPath,
    val version: FileVersion,
    val content: String,
)

sealed interface NotesDirectoryChange {
    val path: NotesDirectoryPath

    data class Created(override val path: NotesDirectoryPath) : NotesDirectoryChange
    data class Modified(override val path: NotesDirectoryPath) : NotesDirectoryChange
    data class Deleted(override val path: NotesDirectoryPath) : NotesDirectoryChange
}

sealed class NotesDirectoryException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AccessDenied(cause: Throwable? = null) : NotesDirectoryException("Access to the notes directory was denied", cause)
    class NotFound(val path: NotesDirectoryPath) : NotesDirectoryException("File was not found: ${path.value}")
    class Conflict(val path: NotesDirectoryPath) : NotesDirectoryException("File changed before it could be written: ${path.value}")
    class InvalidDocument(message: String) : NotesDirectoryException(message)
}

interface NotesDirectoryStorage {
    suspend fun listMarkdownFiles(): List<NotesDirectoryFile>

    suspend fun read(path: NotesDirectoryPath): NotesDirectoryDocument

    suspend fun write(path: NotesDirectoryPath, expectedVersion: FileVersion, content: String): FileVersion

    suspend fun create(path: NotesDirectoryPath, content: String): FileVersion

    suspend fun delete(path: NotesDirectoryPath)

    fun observeChanges(): Flow<NotesDirectoryChange>

    /** Create-or-overwrite a file with raw bytes (binary restore). */
    suspend fun writeBinary(path: NotesDirectoryPath, data: ByteArray): FileVersion =
        create(path, data.decodeToString())
}

data class VaultFileMetadata(
    val path: String,
    val size: Long,
    val modifiedAtMillis: Long,
)

/**
 * Shardkeep's markdown-oriented view over the single filesystem implementation
 * supplied by tresse-kmp. Platform modules only bind these callbacks to a
 * TresseVault; traversal, SAF/filesystem access and `.tresse` handling stay in
 * the library.
 */
class VaultNotesDirectoryStorage(
    private val listFiles: () -> List<VaultFileMetadata>,
    private val readBytes: (path: String, offset: Long, length: Int) -> ByteArray,
    private val writeText: (path: String, content: String) -> Unit,
    private val writeBytes: (path: String, bytes: ByteArray) -> Unit,
    private val deleteFile: (path: String) -> Unit,
) : NotesDirectoryStorage {
    private val changes = MutableSharedFlow<NotesDirectoryChange>(extraBufferCapacity = 64)

    override suspend fun listMarkdownFiles(): List<NotesDirectoryFile> =
        listFiles().asSequence()
            .filter { it.path.endsWith(".md", ignoreCase = true) }
            .map {
                val path = NotesDirectoryPath(it.path)
                NotesDirectoryFile(path, metadataVersion(it), it.size, it.modifiedAtMillis)
            }
            .toList()

    override suspend fun read(path: NotesDirectoryPath): NotesDirectoryDocument {
        val file = metadata(path) ?: throw NotesDirectoryException.NotFound(path)
        require(file.size <= Int.MAX_VALUE) { "Text file is too large: ${path.value}" }
        val content = readBytes(path.value, 0, file.size.toInt()).decodeToString()
        return NotesDirectoryDocument(path, contentVersion(content), content)
    }

    override suspend fun write(
        path: NotesDirectoryPath,
        expectedVersion: FileVersion,
        content: String,
    ): FileVersion {
        val current = read(path)
        if (current.version != expectedVersion) throw NotesDirectoryException.Conflict(path)
        writeText(path.value, content)
        changes.tryEmit(NotesDirectoryChange.Modified(path))
        return contentVersion(content)
    }

    override suspend fun create(path: NotesDirectoryPath, content: String): FileVersion {
        if (metadata(path) != null) throw NotesDirectoryException.Conflict(path)
        writeText(path.value, content)
        changes.tryEmit(NotesDirectoryChange.Created(path))
        return contentVersion(content)
    }

    override suspend fun writeBinary(path: NotesDirectoryPath, data: ByteArray): FileVersion {
        val existed = metadata(path) != null
        writeBytes(path.value, data)
        changes.tryEmit(
            if (existed) NotesDirectoryChange.Modified(path) else NotesDirectoryChange.Created(path),
        )
        return FileVersion("${data.size}:${data.contentHashCode()}")
    }

    override suspend fun delete(path: NotesDirectoryPath) {
        if (metadata(path) == null) throw NotesDirectoryException.NotFound(path)
        deleteFile(path.value)
        changes.tryEmit(NotesDirectoryChange.Deleted(path))
    }

    override fun observeChanges(): Flow<NotesDirectoryChange> = changes

    private fun metadata(path: NotesDirectoryPath): VaultFileMetadata? =
        listFiles().firstOrNull { it.path == path.value }

    private fun metadataVersion(file: VaultFileMetadata) =
        FileVersion("${file.modifiedAtMillis}:${file.size}")

    private fun contentVersion(content: String) = FileVersion("${content.length}:${content.hashCode()}")
}
