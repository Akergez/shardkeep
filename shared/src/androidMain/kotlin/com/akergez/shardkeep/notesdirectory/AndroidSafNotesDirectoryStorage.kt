package com.akergez.shardkeep.notesdirectory

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

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

    override suspend fun listAllFiles(isIgnored: (String) -> Boolean): List<NotesDirectoryFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<NotesDirectoryFile>()
        val cache = mutableMapOf<String, String>()
        // Full-parity sync needs every file the companion plugin sees. Ignored trees
        // are pruned DURING traversal via [isIgnored] (never descended into), matching
        // the plugin; `.tresse` is skipped unconditionally as a safety net.
        collectAllFiles(rootDocId, prefix = "", files, cache, isIgnored)
        docIdCache = cache
        files
    }

    // Size from the document's metadata column — a stat, not a read. The file is
    // never opened, so gigabyte-scale notes directories cost nothing here.
    override suspend fun fileSize(path: NotesDirectoryPath): Long = withContext(Dispatchers.IO) {
        val docId = cachedOrFind(path.value) ?: throw NotesDirectoryException.NotFound(path)
        resolver.query(docUri(docId), SIZE_PROJECTION, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        } ?: 0L
    }

    // True positional read: seek to `offset` via FileChannel and pull at most `len`
    // bytes into a right-sized buffer. Only the requested range is ever resident —
    // this is what lets the CAS engine chunk a gigabyte file without OOM.
    override suspend fun readRange(path: NotesDirectoryPath, offset: Long, len: Long): ByteArray = withContext(Dispatchers.IO) {
        if (len <= 0L) return@withContext ByteArray(0)
        val docId = cachedOrFind(path.value) ?: throw NotesDirectoryException.NotFound(path)
        try {
            resolver.openFileDescriptor(docUri(docId), "r")?.let { pfd ->
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    val channel = input.channel
                    val start = offset.coerceIn(0L, channel.size())
                    val count = len.coerceAtMost(channel.size() - start).toInt()
                    if (count <= 0) return@use ByteArray(0)
                    val buffer = ByteBuffer.allocate(count)
                    channel.position(start)
                    while (buffer.hasRemaining() && channel.read(buffer) >= 0) { /* fill */ }
                    if (buffer.hasRemaining()) buffer.array().copyOf(buffer.position()) else buffer.array()
                }
            } ?: throw NotesDirectoryException.AccessDenied()
        } catch (e: SecurityException) {
            throw NotesDirectoryException.AccessDenied(e)
        }
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

    // ── Local CAS object store (.tresse) — raw, change-silent SAF access ──────

    override suspend fun storeList(dir: String): List<String> = withContext(Dispatchers.IO) {
        val docId = cachedOrFind(dir) ?: return@withContext emptyList()
        val names = mutableListOf<String>()
        val discovered = mutableMapOf<String, String>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        resolver.query(childrenUri, STORE_LIST_PROJECTION, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val childId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                discovered["$dir/$name"] = childId
                names += name
            }
        }
        // One cache merge, not one per child — the content dir can hold thousands.
        if (discovered.isNotEmpty()) docIdCache = docIdCache + discovered
        names
    }

    override suspend fun storeRead(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val docId = cachedOrFind(path) ?: return@withContext null
        try {
            resolver.openInputStream(docUri(docId))?.use { it.readBytes() }
        } catch (e: java.io.FileNotFoundException) {
            null // Stale cached docId for an object deleted underneath us.
        } catch (e: SecurityException) {
            throw NotesDirectoryException.AccessDenied(e)
        }
    }

    override suspend fun storeWrite(path: String, data: ByteArray): Unit = withContext(Dispatchers.IO) {
        // Content-addressed and immutable: an id that already exists holds the
        // correct bytes, so writing it again is wasted SAF I/O. Mirrors FileObjectStore.
        if (cachedOrFind(path) != null) return@withContext
        val parts = path.split('/')
        val parentDocId = ensureParentDirs(parts.dropLast(1))
        val fileUri = DocumentsContract.createDocument(
            resolver, docUri(parentDocId), "application/octet-stream", parts.last(),
        ) ?: throw NotesDirectoryException.AccessDenied()
        docIdCache = docIdCache + (path to DocumentsContract.getDocumentId(fileUri))
        try {
            resolver.openOutputStream(fileUri, "rwt")?.use { it.write(data) }
                ?: throw NotesDirectoryException.AccessDenied()
        } catch (e: SecurityException) {
            throw NotesDirectoryException.AccessDenied(e)
        }
    }

    override suspend fun storeDelete(path: String): Unit = withContext(Dispatchers.IO) {
        val docId = cachedOrFind(path) ?: return@withContext
        try {
            DocumentsContract.deleteDocument(resolver, docUri(docId))
        } catch (e: java.io.FileNotFoundException) {
            // Already gone — idempotent.
        } catch (e: SecurityException) {
            throw NotesDirectoryException.AccessDenied(e)
        }
        docIdCache = docIdCache - path
    }

    override fun observeChanges(): Flow<NotesDirectoryChange> = changes.asSharedFlow()

    // One cursor query per directory fetches all children with metadata in a single IPC call,
    // vs DocumentFile.listFiles() which does N individual calls (one per file).
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
                        files += NotesDirectoryFile(NotesDirectoryPath(path), FileVersion("$lastModified:$size"), size)
                }
            }
        }
    }

    // Like collectMarkdownFiles but keeps EVERY file (text + binary, dot-files
    // included) and does not skip dot-directories — full parity with the companion
    // plugin's directory scan. The lone exception is `.tresse`, which is the CAS
    // object store and must never sync itself. Populates docIdCache so
    // readBytes/writeBinary resolve without re-traversal.
    private fun collectAllFiles(
        parentDocId: String,
        prefix: String,
        files: MutableList<NotesDirectoryFile>,
        cache: MutableMap<String, String>,
        isIgnored: (String) -> Boolean,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        resolver.query(childrenUri, LISTING_PROJECTION, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mimeType = cursor.getString(2) ?: continue
                val lastModified = cursor.getLong(3)
                val size = cursor.getLong(4)
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                // Prune ignored trees before touching them: an ignored dir is never
                // descended into, an ignored file never listed (parity with the
                // plugin's skelAdapterFolder). `.tresse` is always excluded.
                if (prefix.isEmpty() && name == ".tresse") continue
                if (isIgnored(path)) continue
                cache[path] = docId
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collectAllFiles(docId, path, files, cache, isIgnored)
                } else {
                    files += NotesDirectoryFile(NotesDirectoryPath(path), FileVersion("$lastModified:$size"), size)
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
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
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
        val SIZE_PROJECTION = arrayOf(DocumentsContract.Document.COLUMN_SIZE)

        val STORE_LIST_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
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
