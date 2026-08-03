package com.akergez.shardkeep.notesdirectory

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Shardkeep's own view of the notes directory: find the markdown, read it, write it.
 * It owes nothing to tresse. The sync has its own vault and its own ignore patterns,
 * and those patterns say what gets carried to a remote — a question this walk never asks.
 */
class AndroidSafNotesDirectoryStorage(
    context: Context,
    val treeUri: Uri,
) : NotesDirectoryStorage {
    private val resolver = context.applicationContext.contentResolver
    private val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        ?: throw NotesDirectoryException.AccessDenied()
    private val changes = MutableSharedFlow<NotesDirectoryChange>(extraBufferCapacity = 32)

    // Populated by listMarkdownFiles(); maps full directory-relative path to SAF document ID.
    // Allows read/write/delete to skip per-segment tree traversal after a refresh.
    @Volatile private var docIdCache: Map<String, String> = emptyMap()

    override suspend fun listMarkdownFiles(): List<NotesDirectoryFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<NotesDirectoryFile>()
        val cache = mutableMapOf<String, String>()
        collectMarkdownFiles(rootDocId, prefix = "", files, cache)
        docIdCache = cache
        files
    }

    override suspend fun read(path: NotesDirectoryPath): NotesDirectoryDocument = withContext(Dispatchers.IO) {
        val docId = cachedOrFind(path.value) ?: throw NotesDirectoryException.NotFound(path)
        val content = try {
            resolver.openInputStream(docUri(docId))?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw NotesDirectoryException.AccessDenied()
        } catch (e: SecurityException) {
            throw NotesDirectoryException.AccessDenied(e)
        }
        NotesDirectoryDocument(path, contentVersion(content), content)
    }

    override suspend fun write(
        path: NotesDirectoryPath,
        expectedVersion: FileVersion,
        content: String,
    ): FileVersion = withContext(Dispatchers.IO) {
        val docId = cachedOrFind(path.value) ?: throw NotesDirectoryException.NotFound(path)
        val uri = docUri(docId)
        val currentContent = try {
            resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw NotesDirectoryException.AccessDenied()
        } catch (e: SecurityException) {
            throw NotesDirectoryException.AccessDenied(e)
        }
        if (contentVersion(currentContent) != expectedVersion) throw NotesDirectoryException.Conflict(path)
        try {
            resolver.openOutputStream(uri, "rwt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
                ?: throw NotesDirectoryException.AccessDenied()
        } catch (e: SecurityException) {
            throw NotesDirectoryException.AccessDenied(e)
        }
        changes.tryEmit(NotesDirectoryChange.Modified(path))
        contentVersion(content)
    }

    override suspend fun create(path: NotesDirectoryPath, content: String): FileVersion = withContext(Dispatchers.IO) {
        if (cachedOrFind(path.value) != null) throw NotesDirectoryException.Conflict(path)
        val parts = path.value.split('/')
        val parentDocId = ensureParentDirs(parts.dropLast(1))
        val fileUri = DocumentsContract.createDocument(
            resolver, docUri(parentDocId), "text/markdown", parts.last(),
        ) ?: throw NotesDirectoryException.AccessDenied()
        val newDocId = DocumentsContract.getDocumentId(fileUri)
        try {
            resolver.openOutputStream(fileUri, "rwt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
                ?: throw NotesDirectoryException.AccessDenied()
        } catch (e: SecurityException) {
            throw NotesDirectoryException.AccessDenied(e)
        }
        docIdCache = docIdCache + (path.value to newDocId)
        changes.tryEmit(NotesDirectoryChange.Created(path))
        contentVersion(content)
    }

    override suspend fun writeBinary(path: NotesDirectoryPath, data: ByteArray): FileVersion = withContext(Dispatchers.IO) {
        val existing = cachedOrFind(path.value)
        val uri = if (existing != null) {
            docUri(existing)
        } else {
            val parts = path.value.split('/')
            val parentDocId = ensureParentDirs(parts.dropLast(1))
            val fileUri = DocumentsContract.createDocument(
                resolver, docUri(parentDocId), "application/octet-stream", parts.last(),
            ) ?: throw NotesDirectoryException.AccessDenied()
            docIdCache = docIdCache + (path.value to DocumentsContract.getDocumentId(fileUri))
            fileUri
        }
        try {
            resolver.openOutputStream(uri, "rwt")?.use { it.write(data) }
                ?: throw NotesDirectoryException.AccessDenied()
        } catch (e: SecurityException) {
            throw NotesDirectoryException.AccessDenied(e)
        }
        changes.tryEmit(if (existing != null) NotesDirectoryChange.Modified(path) else NotesDirectoryChange.Created(path))
        FileVersion("${data.size}:${data.contentHashCode()}")
    }

    override suspend fun delete(path: NotesDirectoryPath): Unit = withContext(Dispatchers.IO) {
        val docId = cachedOrFind(path.value) ?: throw NotesDirectoryException.NotFound(path)
        try {
            if (!DocumentsContract.deleteDocument(resolver, docUri(docId))) throw NotesDirectoryException.AccessDenied()
        } catch (e: SecurityException) {
            throw NotesDirectoryException.AccessDenied(e)
        }
        docIdCache = docIdCache - path.value
        changes.tryEmit(NotesDirectoryChange.Deleted(path))
    }

    override fun observeChanges(): Flow<NotesDirectoryChange> = changes.asSharedFlow()

    /**
     * One cursor query per directory fetches all children with metadata in a single IPC call,
     * vs DocumentFile.listFiles() which does N individual calls (one per file).
     *
     * Hidden entries are skipped, directories included, and that is the whole rule. Nothing
     * under `.git`, `.obsidian` or `.tresse` is a note, and on SAF every directory entered is
     * a query into another process, so descending into them is pure cost.
     */
    private fun collectMarkdownFiles(
        parentDocId: String,
        prefix: String,
        files: MutableList<NotesDirectoryFile>,
        cache: MutableMap<String, String>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        resolver.query(childrenUri, LISTING_PROJECTION, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mimeType = cursor.getString(2) ?: continue
                val lastModified = cursor.getLong(3)
                val size = cursor.getLong(4)
                if (name.startsWith('.')) continue
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                cache[path] = docId
                when {
                    mimeType == DocumentsContract.Document.MIME_TYPE_DIR ->
                        collectMarkdownFiles(docId, path, files, cache)
                    name.endsWith(".md", ignoreCase = true) ->
                        files += NotesDirectoryFile(
                            NotesDirectoryPath(path),
                            FileVersion("$lastModified:$size"),
                            size,
                            modifiedAtMillis = lastModified,
                        )
                }
            }
        }
    }

    private fun cachedOrFind(path: String): String? = docIdCache[path] ?: traverseToFind(path)

    private fun traverseToFind(path: String): String? {
        var docId = rootDocId
        for (segment in path.split('/')) {
            docId = findChildDocId(docId, segment) ?: return null
        }
        return docId
    }

    private fun findChildDocId(parentDocId: String, name: String): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val childName = cursor.getString(1) ?: continue
                if (childName == name) return docId
            }
        }
        return null
    }

    private fun ensureParentDirs(segments: List<String>): String {
        if (segments.isEmpty()) return rootDocId
        var currentDocId = rootDocId
        var currentPath = ""
        for (segment in segments) {
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            currentDocId = docIdCache[currentPath]
                ?: findChildDocId(currentDocId, segment)
                ?: run {
                    val newUri = DocumentsContract.createDocument(
                        resolver, docUri(currentDocId), DocumentsContract.Document.MIME_TYPE_DIR, segment,
                    ) ?: throw NotesDirectoryException.AccessDenied()
                    val newDocId = DocumentsContract.getDocumentId(newUri)
                    docIdCache = docIdCache + (currentPath to newDocId)
                    newDocId
                }
        }
        return currentDocId
    }

    private fun docUri(docId: String) = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    private fun contentVersion(content: String) = FileVersion("${content.length}:${content.hashCode()}")

    private companion object {
        val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )

        val LISTING_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}
