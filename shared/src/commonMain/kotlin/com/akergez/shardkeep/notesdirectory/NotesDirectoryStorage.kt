package com.akergez.shardkeep.notesdirectory

import kotlinx.coroutines.flow.Flow

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

    /**
     * Every file in the notes directory (text AND binary, including dot-files), NOT
     * just markdown. This is the file universe the CAS sync commits, and it must
     * match the companion sync plugin's file-scan byte-for-byte so the two replicas
     * converge instead of tombstoning each other's files.
     *
     * [isIgnored] is evaluated on each directory-relative path DURING traversal,
     * exactly like the companion plugin's folder walker: an ignored directory is
     * never descended into and an ignored file is never listed — so ignored trees
     * (`.git`, config patterns) are not walked at all while building the file list.
     * `.tresse` is always excluded regardless of the predicate.
     */
    suspend fun listAllFiles(isIgnored: (String) -> Boolean = { false }): List<NotesDirectoryFile> =
        listMarkdownFiles()

    /**
     * Byte length of a file, resolved from stat/metadata WITHOUT reading its
     * contents. This is the CAS engine's `fileLen` step; the notes directory can be
     * gigabytes, so it must never pull the file into memory. The default derives the
     * size from [read] as a fallback for in-memory test storages — real backends
     * override it with a metadata query.
     */
    suspend fun fileSize(path: NotesDirectoryPath): Long = read(path).content.encodeToByteArray().size.toLong()

    /**
     * A positional slice `[offset, offset + len)` of a file's raw bytes. The CAS
     * engine reads every file in bounded chunks through this call, so a real backend
     * must position-read and hold ONLY the requested range in memory — never the
     * whole file. Requests past end-of-file are clamped to what exists. The default
     * buffers via [read] and is only for in-memory test storages.
     */
    suspend fun readRange(path: NotesDirectoryPath, offset: Long, len: Long): ByteArray {
        if (len <= 0L) return ByteArray(0)
        val bytes = read(path).content.encodeToByteArray()
        val start = offset.coerceIn(0L, bytes.size.toLong()).toInt()
        val end = (start.toLong() + len).coerceIn(start.toLong(), bytes.size.toLong()).toInt()
        return bytes.copyOfRange(start, end)
    }

    /** Create-or-overwrite a file with raw bytes (binary restore). */
    suspend fun writeBinary(path: NotesDirectoryPath, data: ByteArray): FileVersion =
        create(path, data.decodeToString())

    // ── Local CAS object store (`.tresse/{meta,content}`) ─────────────────────
    // Raw access to the notes directory's OWN local repo — its `.git`. These operate
    // on engine-internal object files by directory-relative path and MUST NOT emit
    // observeChanges events (a CAS object is not user content). Defaults are
    // no-ops for in-memory test storages that host no `.tresse`; the SAF backend
    // overrides them. `dir`/`path` are directory-relative (e.g. `.tresse/content`).

    /** Direct child file names under `dir` (non-recursive); empty if absent. */
    suspend fun storeList(dir: String): List<String> = emptyList()

    /** Raw bytes of an object file, or null if it does not exist. */
    suspend fun storeRead(path: String): ByteArray? = null

    /** Create-or-overwrite an object file, creating parent dirs as needed. */
    suspend fun storeWrite(path: String, data: ByteArray) {}

    /** Delete an object file. Idempotent — a missing file is not an error. */
    suspend fun storeDelete(path: String) {}
}
