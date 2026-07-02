package com.akergez.shardkeep.sync

import com.akergez.shardkeep.notesdirectory.NotesDirectoryException
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath
import com.akergez.shardkeep.notesdirectory.NotesDirectoryStorage
import org.json.JSONObject

object TresseConfigFile {
    private val path = NotesDirectoryPath(".tresse/config.toml")
    private val legacyPath = NotesDirectoryPath(".tresse.toml")

    suspend fun read(storage: NotesDirectoryStorage): TresseConfig? {
        val document = try {
            storage.read(path)
        } catch (_: NotesDirectoryException.NotFound) {
            try { storage.read(legacyPath) } catch (_: NotesDirectoryException.NotFound) { return null }
        }
        return TresseConfigString.decode(document.content)
    }

    suspend fun write(storage: NotesDirectoryStorage, config: TresseConfig) {
        val content = serialize(config)
        val current = try { storage.read(path) } catch (_: NotesDirectoryException.NotFound) { null }
        if (current == null) storage.create(path, content)
        else storage.write(path, current.version, content)
    }

    fun serialize(config: TresseConfig): String = buildString {
        appendLine("[ignore]")
        appendLine("patterns = [${config.ignorePatterns.joinToString(", ") { JSONObject.quote(it) }}]")
        appendLine()
        // The [sync] block (enabled / interval / debounce) is device-local and
        // kept in SharedPreferences, not in this version-controlled file.
        appendLine("[remote]")
        appendLine("storage_type = ${JSONObject.quote(if (config.storageType == "s3") "s3" else "http")}")
        appendLine("url = ${JSONObject.quote(config.serverUrl)}")
        appendLine("token = ${JSONObject.quote(config.token)}")
        appendLine("repo_id = ${JSONObject.quote(config.repoId)}")
        appendLine("encryption_key = ${JSONObject.quote(config.encryptionKey)}")
        appendLine("s3_endpoint = ${JSONObject.quote(config.s3Endpoint)}")
        appendLine("s3_region = ${JSONObject.quote(config.s3Region)}")
        appendLine("s3_bucket = ${JSONObject.quote(config.s3Bucket)}")
        appendLine("s3_access_key_id = ${JSONObject.quote(config.s3AccessKeyId)}")
        appendLine("s3_secret_access_key = ${JSONObject.quote(config.s3SecretAccessKey)}")
    }
}
