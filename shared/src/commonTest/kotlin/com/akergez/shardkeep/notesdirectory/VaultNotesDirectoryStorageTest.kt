package com.akergez.shardkeep.notesdirectory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class VaultNotesDirectoryStorageTest {
    @Test
    fun writesAndReadsThroughVaultCallbacks() = runTest {
        val files = mutableMapOf<String, ByteArray>()
        val storage = storage(files)

        storage.create(NotesDirectoryPath("notes/a.md"), "hello")

        assertEquals("hello", storage.read(NotesDirectoryPath("notes/a.md")).content)
    }

    @Test
    fun rejectsWriteWhenExpectedContentVersionIsStale() = runTest {
        val files = mutableMapOf("a.md" to "first".encodeToByteArray())
        val storage = storage(files)
        val stale = storage.read(NotesDirectoryPath("a.md")).version
        files["a.md"] = "changed elsewhere".encodeToByteArray()

        assertFailsWith<NotesDirectoryException.Conflict> {
            storage.write(NotesDirectoryPath("a.md"), stale, "mine")
        }
    }

    private fun storage(files: MutableMap<String, ByteArray>) = VaultNotesDirectoryStorage(
        listFiles = {
            files.map { (path, bytes) ->
                VaultFileMetadata(path, bytes.size.toLong(), 0)
            }
        },
        readBytes = { path, offset, length ->
            val bytes = files.getValue(path)
            val start = offset.toInt().coerceAtMost(bytes.size)
            bytes.copyOfRange(start, (start + length).coerceAtMost(bytes.size))
        },
        writeText = { path, content -> files[path] = content.encodeToByteArray() },
        writeBytes = { path, bytes -> files[path] = bytes },
        deleteFile = files::remove,
    )
}
