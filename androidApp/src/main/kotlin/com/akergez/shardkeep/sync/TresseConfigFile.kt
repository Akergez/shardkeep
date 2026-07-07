package com.akergez.shardkeep.sync

import com.akergez.shardkeep.notesdirectory.NotesDirectoryException
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath
import com.akergez.shardkeep.notesdirectory.NotesDirectoryStorage
import com.akergez.tresse.kmp.TresseConfigString
import com.akergez.tresse.kmp.TresseConfigFile as KmpConfigFile

/**
 * Reads/writes `.tresse/config.toml` in the notes directory. Parsing and
 * serialization are delegated to the shared `tresse-kmp` library; Shardkeep keeps
 * the notes-directory specifics: version-aware writes through the regular
 * (event-emitting) file API rather than the CAS
 * store, so the shared config stays version-controlled like any other file.
 */
object TresseConfigFile {
    private val path = NotesDirectoryPath(".tresse/config.toml")

    suspend fun read(storage: NotesDirectoryStorage): TresseConfig? {
        val document = try {
            storage.read(path)
        } catch (_: NotesDirectoryException.NotFound) {
            return null
        }
        return TresseConfigString.decode(document.content)
    }

    suspend fun write(storage: NotesDirectoryStorage, config: TresseConfig) {
        val content = KmpConfigFile.serialize(config)
        val current = try { storage.read(path) } catch (_: NotesDirectoryException.NotFound) { null }
        if (current == null) storage.create(path, content)
        else storage.write(path, current.version, content)
    }
}
