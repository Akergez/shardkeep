package com.akergez.shardkeep.sync

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Parses both the plugin's config.toml and its optional tresse1 share string. */
object TresseConfigString {
    private const val SHARE_PREFIX = "tresse1:"

    fun decode(input: String): TresseConfig {
        val text = input.trim()
        return if (text.startsWith(SHARE_PREFIX)) decodeShareString(text) else decodeToml(text)
    }

    private fun decodeShareString(value: String): TresseConfig {
        val json = try {
            Base64.decode(
                value.substring(SHARE_PREFIX.length),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            ).toString(Charsets.UTF_8)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Corrupt Tresse config string", error)
        }
        val obj = try { JSONObject(json) } catch (error: Exception) {
            throw IllegalArgumentException("Tresse config does not contain valid JSON", error)
        }
        return TresseConfig(
            serverUrl = obj.string("serverUrl"),
            repoId = obj.string("repoId"),
            token = obj.string("token"),
            encryptionKey = obj.string("encryptionKey"),
            storageType = if (obj.string("storageType") == "s3") "s3" else "http",
            s3Endpoint = obj.string("s3Endpoint"),
            s3Region = obj.string("s3Region"),
            s3Bucket = obj.string("s3Bucket"),
            s3AccessKeyId = obj.string("s3AccessKeyId"),
            s3SecretAccessKey = obj.string("s3SecretAccessKey"),
            ignorePatterns = obj.strings("ignorePatterns", DEFAULT_IGNORES),
            autoSyncEnabled = obj.boolean("autoSyncEnabled", false),
            autoSyncIntervalMinutes = obj.number("autoSyncIntervalMinutes", 15),
            autoSyncDebounceSeconds = obj.number("autoSyncDebounceSeconds", 10),
        )
    }

    /** Port of the companion sync plugin's config.ts parseConfig. */
    private fun decodeToml(text: String): TresseConfig {
        val values = mutableMapOf<String, Any>()
        val lines = text.split(Regex("\\r?\\n"))
        var section = ""
        var index = 0
        while (index < lines.size) {
            val line = stripComment(lines[index]).trim()
            if (line.isEmpty()) { index++; continue }
            val header = HEADER.matchEntire(line)
            if (header != null) {
                section = header.groupValues[1]
                index++
                continue
            }
            val assignment = ASSIGNMENT.matchEntire(line)
                ?: throw IllegalArgumentException("invalid TOML at line ${index + 1}")
            var valueText = assignment.groupValues[2]
            if (valueText.startsWith("[") && !arrayIsClosed(valueText)) {
                while (++index < lines.size) {
                    valueText += "\n${stripComment(lines[index]).trim()}"
                    if (arrayIsClosed(valueText)) break
                }
            }
            values["$section.${assignment.groupValues[1]}"] = parseValue(valueText, index + 1)
            index++
        }
        fun string(key: String) = values[key] as? String
        @Suppress("UNCHECKED_CAST")
        val ignores = values["ignore.patterns"] as? List<String> ?: DEFAULT_IGNORES
        // The [sync] block (enabled / interval / debounce) is device-local: it is
        // not read from the shared config.toml but overlaid from SharedPreferences.
        return TresseConfig(
            serverUrl = string("remote.url").orEmpty(),
            repoId = string("remote.repo_id").orEmpty(),
            token = string("remote.token").orEmpty(),
            encryptionKey = string("remote.encryption_key").orEmpty(),
            storageType = if (string("remote.storage_type") == "s3") "s3" else "http",
            s3Endpoint = string("remote.s3_endpoint").orEmpty(),
            s3Region = string("remote.s3_region").orEmpty(),
            s3Bucket = string("remote.s3_bucket").orEmpty(),
            s3AccessKeyId = string("remote.s3_access_key_id").orEmpty(),
            s3SecretAccessKey = string("remote.s3_secret_access_key").orEmpty(),
            ignorePatterns = ignores,
        )
    }

    private fun parseValue(input: String, line: Int): Any = try {
        val normalized = input.replace(Regex(",\\s*]$"), "]")
        when (val value = JSONObject("{\"v\":$normalized}").get("v")) {
            is String, is Boolean, is Number -> value
            is JSONArray -> List(value.length()) { value.get(it) }.also { list ->
                require(list.all { it is String })
            }
            else -> error("unsupported")
        }
    } catch (_: Exception) {
        throw IllegalArgumentException("expected a string, number, boolean, or string array at line $line")
    }

    private fun stripComment(line: String): String {
        var quoted = false
        var escaped = false
        line.forEachIndexed { index, char ->
            if (quoted && char == '\\' && !escaped) { escaped = true; return@forEachIndexed }
            if (char == '"' && !escaped) quoted = !quoted
            if (char == '#' && !quoted) return line.substring(0, index)
            escaped = false
        }
        return line
    }

    private fun arrayIsClosed(input: String): Boolean {
        var quoted = false
        var escaped = false
        for (char in input) {
            if (quoted && char == '\\' && !escaped) { escaped = true; continue }
            if (char == '"' && !escaped) quoted = !quoted
            if (char == ']' && !quoted) return true
            escaped = false
        }
        return false
    }

    private fun JSONObject.string(key: String) = if (opt(key) is String) getString(key) else ""
    private fun JSONObject.boolean(key: String, fallback: Boolean) = if (opt(key) is Boolean) getBoolean(key) else fallback
    private fun JSONObject.number(key: String, fallback: Long) = if (opt(key) is Number) getLong(key) else fallback
    private fun JSONObject.strings(key: String, fallback: List<String>): List<String> {
        val array = optJSONArray(key) ?: return fallback
        return List(array.length()) { array.opt(it) }.filterIsInstance<String>()
    }

    private val HEADER = Regex("^\\[([A-Za-z0-9_-]+)]$")
    private val ASSIGNMENT = Regex("^([A-Za-z0-9_-]+)\\s*=\\s*(.+)$")
    private val DEFAULT_IGNORES = listOf(".git", ".trash", ".obsidian")
}
