package com.akergez.shardkeep

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.akergez.shardkeep.domain.model.TaskFormat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WorkspaceRecord(
    val id: String,
    val name: String,
    val treeUri: Uri,
    val taskFormat: TaskFormat?,
)

class WorkspaceRegistry(context: Context) {
    private val appContext = context.applicationContext
    private val root = appContext.getSharedPreferences(ROOT_PREFERENCES, Context.MODE_PRIVATE)

    fun list(): List<WorkspaceRecord> {
        migrateLegacyIfNeeded()
        val array = runCatching { JSONArray(root.getString(KEY_ITEMS, "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val uri = item.optString("treeUri").takeIf(String::isNotBlank) ?: return@mapNotNull null
            WorkspaceRecord(
                id = item.optString("id"),
                name = item.optString("name").ifBlank {
                    appContext.getString(R.string.workspace_numbered, index + 1)
                },
                treeUri = Uri.parse(uri),
                taskFormat = item.optString("taskFormat").takeIf(String::isNotBlank)
                    ?.let { runCatching { TaskFormat.valueOf(it) }.getOrNull() },
            )
        }
    }

    fun active(): WorkspaceRecord? {
        val items = list()
        val activeId = root.getString(KEY_ACTIVE_ID, null)
        return items.firstOrNull { it.id == activeId } ?: items.firstOrNull()
    }

    fun select(id: String) {
        if (list().any { it.id == id }) root.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun add(name: String, treeUri: Uri): WorkspaceRecord {
        val record = WorkspaceRecord(
            UUID.randomUUID().toString(),
            name.ifBlank { appContext.getString(R.string.workspace_default) },
            treeUri,
            null,
        )
        save(list() + record)
        select(record.id)
        return record
    }

    fun update(record: WorkspaceRecord) = save(list().map { if (it.id == record.id) record else it })

    fun preferences(id: String): SharedPreferences =
        appContext.getSharedPreferences("workspace_$id", Context.MODE_PRIVATE)

    private fun save(items: List<WorkspaceRecord>) {
        val array = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("treeUri", item.treeUri.toString())
                    put("taskFormat", item.taskFormat?.name ?: "")
                })
            }
        }
        root.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun migrateLegacyIfNeeded() {
        if (root.contains(KEY_ITEMS)) return
        val uri = root.getString("tree_uri", null) ?: run {
            root.edit().putString(KEY_ITEMS, "[]").apply()
            return
        }
        val id = UUID.randomUUID().toString()
        val format = root.getString("task_format", null)
        val name = Uri.parse(uri).lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank) ?: "Shardkeep"
        save(listOf(WorkspaceRecord(id, name, Uri.parse(uri), format?.let { runCatching { TaskFormat.valueOf(it) }.getOrNull() })))
        root.edit().putString(KEY_ACTIVE_ID, id).apply()
        val scoped = preferences(id)
        scoped.edit()
            .putString("quick_notes_folder", root.getString("quick_notes_folder", null))
            .putBoolean("notifications_enabled", root.getBoolean("notifications_enabled", true))
            .putBoolean("sync_auto_enabled", root.getBoolean("sync_auto_enabled", false))
            .putLong("sync_interval_minutes", (root.all["sync_interval_minutes"] as? Number)?.toLong() ?: 15L)
            .putLong("sync_debounce_seconds", (root.all["sync_debounce_seconds"] as? Number)?.toLong() ?: 5L)
            .apply()
    }

    companion object {
        const val ROOT_PREFERENCES = "notes_directory"
        private const val KEY_ITEMS = "workspaces_json"
        private const val KEY_ACTIVE_ID = "active_workspace_id"
    }
}
