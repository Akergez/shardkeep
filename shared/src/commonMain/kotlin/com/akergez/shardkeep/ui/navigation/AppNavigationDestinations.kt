package com.akergez.shardkeep.ui.navigation

import androidx.compose.runtime.Composable
import com.akergez.shardkeep.ui.AppSection
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.all_tasks
import shardkeep.shared.generated.resources.ic_article
import shardkeep.shared.generated.resources.ic_calendar_today
import shardkeep.shared.generated.resources.ic_format_list
import shardkeep.shared.generated.resources.ic_settings
import shardkeep.shared.generated.resources.ic_sync
import shardkeep.shared.generated.resources.quick_notes
import shardkeep.shared.generated.resources.quick_notes_short
import shardkeep.shared.generated.resources.settings
import shardkeep.shared.generated.resources.sync_title
import shardkeep.shared.generated.resources.sync_title_short
import shardkeep.shared.generated.resources.tasks

/**
 * Единственный список пунктов навигации: три контейнера рисуют его каждый по-своему,
 * но состав и порядок задаются здесь, чтобы они не разъехались.
 *
 * [compact] — для нижней панели и рельса, где на подпись приходится узкая колонка:
 * там длинные названия заменены короткими.
 */
@Composable
fun AppNavigationDestinations(
    showNotes: Boolean,
    compact: Boolean = false,
    item: @Composable (AppSection, DrawableResource, StringResource) -> Unit,
) {
    item(AppSection.Tasks, Res.drawable.ic_calendar_today, Res.string.tasks)
    item(AppSection.AllTasks, Res.drawable.ic_format_list, Res.string.all_tasks)
    if (showNotes) {
        val notes = if (compact) Res.string.quick_notes_short else Res.string.quick_notes
        item(AppSection.Notes, Res.drawable.ic_article, notes)
    }
    val sync = if (compact) Res.string.sync_title_short else Res.string.sync_title
    item(AppSection.Sync, Res.drawable.ic_sync, sync)
    item(AppSection.Settings, Res.drawable.ic_settings, Res.string.settings)
}
