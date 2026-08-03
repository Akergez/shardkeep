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
