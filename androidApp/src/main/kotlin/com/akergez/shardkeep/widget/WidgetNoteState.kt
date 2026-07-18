package com.akergez.shardkeep.widget

import androidx.datastore.preferences.core.stringPreferencesKey
import com.akergez.shardkeep.domain.model.QuickNote
import org.json.JSONArray
import org.json.JSONObject

internal object WidgetNoteState {
    val NOTES = stringPreferencesKey("notes_json")
    val CHECKLISTS = stringPreferencesKey("checklists_json")

    fun encodeNotes(notes: List<QuickNote>): String = JSONArray().apply {
        notes.filterIsInstance<QuickNote.Text>().forEach { note ->
            put(JSONObject().put("path", note.id.path).put("title", note.title).put("content", note.content))
        }
    }.toString()

    fun decodeNotes(raw: String?): List<WidgetNote> {
        val array = raw?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { WidgetNote(it.optString("path"), it.optString("title"), it.optString("content")) }
        }
    }

    fun encodeChecklists(notes: List<QuickNote>): String = JSONArray().apply {
        notes.filterIsInstance<QuickNote.Checklist>().forEach { checklist ->
            put(JSONObject().put("path", checklist.id.path).put("title", checklist.title).put(
                "items",
                JSONArray().apply {
                    checklist.items.forEach { item ->
                        put(JSONObject().put("index", item.index).put("text", item.text).put("checked", item.isChecked))
                    }
                },
            ))
        }
    }.toString()

    fun decodeChecklists(raw: String?): List<WidgetChecklist> {
        val array = raw?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val checklist = array.optJSONObject(index) ?: return@mapNotNull null
            val items = checklist.optJSONArray("items") ?: JSONArray()
            WidgetChecklist(
                path = checklist.optString("path"),
                title = checklist.optString("title"),
                items = (0 until items.length()).mapNotNull { itemIndex ->
                    items.optJSONObject(itemIndex)?.let {
                        WidgetChecklistItem(it.optInt("index"), it.optString("text"), it.optBoolean("checked"))
                    }
                },
            )
        }
    }
}
