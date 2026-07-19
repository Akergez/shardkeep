package com.akergez.shardkeep

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.akergez.shardkeep.data.NotesDirectoryQuickNoteRepository
import com.akergez.shardkeep.notesdirectory.AndroidSafNotesDirectoryStorage
import com.akergez.shardkeep.notesdirectory.NotesDirectoryPath
import com.akergez.shardkeep.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface ShareNoteSaver {
    suspend fun save(
        workspace: WorkspaceRecord,
        title: String,
        text: String,
        imageUris: List<Uri>,
    )
}

class AndroidShareNoteSaver(context: Context) : ShareNoteSaver {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override suspend fun save(
        workspace: WorkspaceRecord,
        title: String,
        text: String,
        imageUris: List<Uri>,
    ) = withContext(Dispatchers.IO) {
        val registry = WorkspaceRegistry(appContext)
        val folder = registry.preferences(workspace.id).getString("quick_notes_folder", null)
            ?: NotesDirectoryQuickNoteRepository.QUICK_NOTES_FOLDER
        val storage = AndroidSafNotesDirectoryStorage(appContext, workspace.treeUri)
        val stamp = System.currentTimeMillis()
        val imageLinks = imageUris.mapIndexed { index, uri ->
            val original = displayName(uri)
            val extension = original.substringAfterLast('.', missingDelimiterValue = "")
                .ifBlank { MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)).orEmpty() }
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                ?: "jpg"
            val safeStem = original.substringBeforeLast('.', original)
                .replace(Regex("[^A-Za-z0-9._-]"), "-")
                .trim('-', '.')
                .take(48)
                .ifBlank { "image" }
            val name = "$safeStem-$stamp-${index + 1}.$extension"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Cannot read shared image")
            storage.writeBinary(NotesDirectoryPath("$folder/Attachments/$name"), bytes)
            "![${escapeMarkdownAlt(original)}](Attachments/$name)"
        }
        val content = buildList {
            text.takeIf(String::isNotBlank)?.let(::add)
            addAll(imageLinks)
        }.joinToString("\n\n")
        NotesDirectoryQuickNoteRepository(storage, folder).createTextNote(title, content)
        registry.select(workspace.id)
        runCatching { WidgetUpdater.publishNotes(appContext) }
        Unit
    }

    private fun displayName(uri: Uri): String {
        val cursorName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) null
                else cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 }
                    ?.let(cursor::getString)
            }
        return cursorName?.takeIf(String::isNotBlank) ?: uri.lastPathSegment ?: "image"
    }
}

private fun escapeMarkdownAlt(value: String): String =
    value.replace("[", "\\[").replace("]", "\\]")
