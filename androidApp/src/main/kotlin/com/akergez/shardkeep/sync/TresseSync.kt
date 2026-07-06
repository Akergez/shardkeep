package com.akergez.shardkeep.sync

import android.content.SharedPreferences
import com.akergez.shardkeep.SyncStatusStore
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath
import com.akergez.shardkeep.notesdirectory.NotesDirectoryStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.tresse_ffi.FfiObjectStore
import uniffi.tresse_ffi.FfiRemoteKv
import uniffi.tresse_ffi.FfiRestoreSink
import uniffi.tresse_ffi.FfiVaultFile
import uniffi.tresse_ffi.FfiVaultSource
import uniffi.tresse_ffi.TresseException
import uniffi.tresse_ffi.casSync

data class TresseConfig(
    val serverUrl: String,
    val repoId: String,
    val token: String,
    val encryptionKey: String,
    val storageType: String = "http",
    val s3Endpoint: String = "",
    val s3Region: String = "",
    val s3Bucket: String = "",
    val s3AccessKeyId: String = "",
    val s3SecretAccessKey: String = "",
    val ignorePatterns: List<String> = listOf(".git", ".trash", ".obsidian"),
    val autoSyncEnabled: Boolean = true,
    val autoSyncIntervalMinutes: Long = 15,
    val autoSyncDebounceSeconds: Long = 10,
)

data class TresseSyncResult(val pushed: Int, val pulled: Int, val changed: Boolean)

// Auto-sync settings are local to this Android device and are deliberately
// absent from the notes directory's shared config.toml.
private const val KEY_SYNC_ENABLED = "sync_auto_enabled"
private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
private const val KEY_SYNC_DEBOUNCE = "sync_debounce_seconds"

internal fun TresseConfig.withLocalSync(prefs: SharedPreferences): TresseConfig = copy(
    autoSyncEnabled = prefs.getBoolean(KEY_SYNC_ENABLED, false),
    autoSyncIntervalMinutes = prefs.getLong(KEY_SYNC_INTERVAL, autoSyncIntervalMinutes),
    autoSyncDebounceSeconds = prefs.getLong(KEY_SYNC_DEBOUNCE, autoSyncDebounceSeconds),
)

internal fun SharedPreferences.saveLocalSync(config: TresseConfig) {
    edit()
        .putBoolean(KEY_SYNC_ENABLED, config.autoSyncEnabled)
        .putLong(KEY_SYNC_INTERVAL, config.autoSyncIntervalMinutes)
        .putLong(KEY_SYNC_DEBOUNCE, config.autoSyncDebounceSeconds)
        .apply()
}

/** Short human-readable summary of a finished sync, shared by manual and background paths. */
fun summaryOf(result: TresseSyncResult): String =
    if (result.pushed == 0 && result.pulled == 0) "Уже актуально"
    else "Отправлено ${result.pushed}, получено ${result.pulled}"

/**
 * Android host for the content-addressed (CAS) sync protocol.
 *
 * The entire commit → sync → restore flow runs inside the Rust `cas_sync`
 * engine (see `tresse-ffi`), the SAME code the companion plugin drives through
 * WASM and `tresse-cli` drives on disk. Android only supplies the four sides
 * the engine can't reach on its own:
 *  - [remoteKvOf] — the encrypted remote object store (HTTP / S3);
 *  - [NotesDirectoryObjectStore] — the notes directory's OWN local CAS, `.tresse/{meta,content}`;
 *  - [NotesDirectorySource] — reading the SAF notes directory for the commit skeleton;
 *  - [NotesDirectorySink] — writing the SAF notes directory during restore.
 * The local store IS the `.tresse` inside the notes directory — the notes
 * directory's `.git`, the single local source of truth, shared with any other
 * client on the device (the companion plugin). There is NO private per-app
 * object store or mirror.
 */
class TresseSync(private val notesDirectory: NotesDirectoryStorage) {

    suspend fun perform(
        config: TresseConfig,
        progress: SyncStatusStore? = null,
    ): TresseSyncResult = syncMutex.withLock { withContext(Dispatchers.IO) {
        require(config.encryptionKey.isNotBlank()) { "Tresse encryption key is required" }
        require(config.isComplete()) { "Tresse ${config.storageType} sync configuration is incomplete" }

        val localStore = NotesDirectoryObjectStore(notesDirectory)
        val outcome = casSync(
            encryptionKeyB64 = config.encryptionKey,
            fileIndexJson = localStore.readFileIndex(),
            local = localStore,
            remote = CountingRemoteKv(remoteKvOf(config), progress),
            source = NotesDirectorySource(notesDirectory, config, progress),
            sink = NotesDirectorySink(notesDirectory, config, progress),
        )
        localStore.writeFileIndex(outcome.fileIndexJson)
        TresseSyncResult(
            pushed = outcome.pushed.toInt(),
            pulled = outcome.pulled.toInt(),
            changed = outcome.committed || outcome.pulled > 0u || outcome.restored > 0u || outcome.removed > 0u,
        )
    } }

    private companion object {
        /** Serializes manual and WorkManager commit/sync/restore transactions. */
        val syncMutex = Mutex()
    }
}

/** `.tresse` and the configured ignore folders never take part in a snapshot. */
private fun ignoredPath(path: String, config: TresseConfig): Boolean =
    path == ".tresse" || path.startsWith(".tresse/") || path.split('/').any { it.startsWith(".") } ||
        config.ignorePatterns.any { it.isNotBlank() && (path == it || path.startsWith("$it/")) }

/**
 * Text-vs-binary classification. MUST match the companion plugin's
 * `TEXT_EXTENSIONS` (`obsidian-plugin/src/vault-scan.ts`) exactly: the two
 * replicas have to agree on every file's File/Binary kind, or their commit
 * skeletons diverge and they ping-pong.
 */
private val TEXT_EXTENSIONS = setOf(
    "md", "txt", "json", "js", "ts", "tsx", "jsx", "css", "html", "toml",
    "yaml", "yml", "xml", "py", "rs", "go", "c", "cpp", "h", "hpp", "sh", "bat",
)

private fun isTextPath(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS

/**
 * Read side of the notes directory for the commit engine. The engine drives
 * this exactly like the plugin's source: `len(path)` supplies each file's size
 * for chunk planning, then `read(path, offset, len)` streams the file back in
 * bounded ranges. Nothing is buffered here — a range request goes straight to
 * a position-read of the notes directory, so a gigabyte file never lands in
 * memory at once. All methods are synchronous (the engine calls them on the
 * `Dispatchers.IO` thread already running `perform`), so the suspend notes-
 * directory calls are bridged with `runBlocking`. `len`/`read` are infallible
 * from the engine's view — they return a zero size / empty range on error
 * rather than throwing across the FFI.
 */
private class NotesDirectorySource(
    private val notesDirectory: NotesDirectoryStorage,
    private val config: TresseConfig,
    private val progress: SyncStatusStore?,
) : FfiVaultSource {
    override fun listFiles(): List<FfiVaultFile> = try {
        runBlocking {
            // Ignore patterns (from config.toml) + `.tresse` are pruned during the
            // scan itself, so ignored trees are never walked while building the skel.
            notesDirectory.listAllFiles { ignoredPath(it, config) }
                .distinctBy { it.path.value }
                .sortedBy { it.path.value }
                .map {
                    val path = it.path.value
                    val mtime = it.version.value.substringBefore(':').toULongOrNull() ?: 0uL
                    FfiVaultFile(path = path, binary = !isTextPath(path), mtimeMs = mtime, size = it.size.coerceAtLeast(0).toULong())
                }
        }
    } catch (error: Throwable) {
        throw TresseException.General("listing notes directory failed: ${error.message}")
    }

    override fun len(path: String): ULong =
        runCatching { runBlocking { notesDirectory.fileSize(NotesDirectoryPath(path)) } }
            .getOrDefault(0L).coerceAtLeast(0L).toULong()

    override fun read(path: String, offset: ULong, len: ULong): ByteArray {
        // Count one read per file, at its first range. The counter must NOT live in
        // len(): the commit engine sizes a text file up to twice per pass (once for
        // the object-fit check, once inside `fetch`), so counting `len` double-counts.
        // A file's content is actually pulled starting at offset 0 exactly once.
        if (offset == 0uL) progress?.reportCommitRead()
        return runCatching { runBlocking { notesDirectory.readRange(NotesDirectoryPath(path), offset.toLong(), len.toLong()) } }
            .getOrDefault(ByteArray(0))
    }
}

/**
 * Write side of the notes directory for restore. Uses optimistic-concurrency
 * writes: a snapshot of the current file versions decides create vs. write,
 * and tombstoned paths are deleted (deletions made on another device now
 * propagate here).
 */
private class NotesDirectorySink(
    private val notesDirectory: NotesDirectoryStorage,
    private val config: TresseConfig,
    private val progress: SyncStatusStore?,
) : FfiRestoreSink {
    // Which paths already exist decides create vs. write. This is seeded from the
    // cheap metadata listing (mtime:size) — used ONLY as a presence set, never as
    // a concurrency token: the listing's version format is not comparable to the
    // content-based version write() expects. The token is read fresh below.
    private val known: MutableSet<String> = runBlocking {
        notesDirectory.listAllFiles { ignoredPath(it, config) }.mapTo(mutableSetOf()) { it.path.value }
    }

    override fun writeText(path: String, content: String) {
        if (ignoredPath(path, config)) return
        try {
            runBlocking {
                val target = NotesDirectoryPath(path)
                if (path in known) {
                    // read() yields the current content-based version write() checks
                    // against; the sink is the sole writer here, so it always matches
                    // unless the file truly changed underneath us.
                    val current = notesDirectory.read(target).version
                    notesDirectory.write(target, current, content)
                } else {
                    notesDirectory.create(target, content)
                    known += path
                }
            }
            progress?.reportApply()
        } catch (error: Throwable) {
            throw TresseException.General("writing $path failed: ${error.message}")
        }
    }

    override fun writeBinary(path: String, data: ByteArray) {
        if (ignoredPath(path, config)) return
        try {
            runBlocking { notesDirectory.writeBinary(NotesDirectoryPath(path), data) }
            known += path
            progress?.reportApply()
        } catch (error: Throwable) {
            throw TresseException.General("writing $path failed: ${error.message}")
        }
    }

    override fun remove(path: String) {
        if (ignoredPath(path, config)) return
        runBlocking { runCatching { notesDirectory.delete(NotesDirectoryPath(path)) } }
        known.remove(path)
        progress?.reportApply()
    }
}

/**
 * The notes directory's own local content-addressed store: `.tresse/{meta,content}`
 * INSIDE the notes directory, reached over SAF. This is the replica's `.git` —
 * the single local source of truth, shared with any other client on the device
 * (the companion plugin); there is no private per-app copy. Objects are stored
 * plaintext under a filename equal to their content id (the Rust side derives
 * and verifies the id). All methods are synchronous for the FFI; the suspend
 * notes-directory calls are bridged with `runBlocking` on the engine's IO thread.
 */
private class NotesDirectoryObjectStore(private val notesDirectory: NotesDirectoryStorage) : FfiObjectStore {
    fun readFileIndex(): String? = runBlocking {
        notesDirectory.storeRead(".tresse/file-index.json")?.decodeToString()
    }

    fun writeFileIndex(json: String) = runBlocking {
        notesDirectory.storeWrite(".tresse/file-index.json", json.encodeToByteArray())
    }
    override fun list(`class`: String): List<String> = try {
        runBlocking { notesDirectory.storeList(".tresse/$`class`") }
    } catch (error: Throwable) {
        throw TresseException.General("listing .tresse/$`class` failed: ${error.message}")
    }

    // null → the object is absent; the engine reads it from the remote instead.
    override fun get(`class`: String, id: String): ByteArray? = try {
        runBlocking { notesDirectory.storeRead(".tresse/$`class`/$id") }
    } catch (error: Throwable) {
        throw TresseException.General("reading .tresse/$`class`/$id failed: ${error.message}")
    }

    override fun put(`class`: String, id: String, bytes: ByteArray) {
        try {
            runBlocking { notesDirectory.storeWrite(".tresse/$`class`/$id", bytes) }
        } catch (error: Throwable) {
            throw TresseException.General("writing .tresse/$`class`/$id failed: ${error.message}")
        }
    }

    override fun delete(`class`: String, id: String) {
        try {
            runBlocking { notesDirectory.storeDelete(".tresse/$`class`/$id") }
        } catch (error: Throwable) {
            throw TresseException.General("deleting .tresse/$`class`/$id failed: ${error.message}")
        }
    }
}

/**
 * Wraps the real remote store to count object transfers as the engine performs
 * them: every `put` is one object pushed, every `get` one pulled. This is the
 * only honest source of live progress on Android — the FFI is one blocking call,
 * but it drives these methods throughout the sync.
 */
private class CountingRemoteKv(
    private val inner: FfiRemoteKv,
    private val progress: SyncStatusStore?,
) : FfiRemoteKv {
    override fun list(prefix: String): List<String> = inner.list(prefix)

    override fun get(key: String): ByteArray = inner.get(key).also { progress?.reportDownload() }

    override fun put(key: String, value: ByteArray) {
        inner.put(key, value)
        progress?.reportUpload()
    }

    override fun delete(key: String) = inner.delete(key)
}
