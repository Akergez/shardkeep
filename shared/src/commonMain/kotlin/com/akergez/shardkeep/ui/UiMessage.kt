package com.akergez.shardkeep.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.note_delete_error
import shardkeep.shared.generated.resources.note_save_error
import shardkeep.shared.generated.resources.note_update_error
import shardkeep.shared.generated.resources.refresh_complete
import shardkeep.shared.generated.resources.refresh_error
import shardkeep.shared.generated.resources.schedule_today_error
import shardkeep.shared.generated.resources.settings_saved
import shardkeep.shared.generated.resources.task_save_error

/**
 * То, о чём модель просит сообщить пользователю. Модель называет событие, а не текст:
 * локаль и ресурсы — дело отрисовки, и до неё строка не должна доживать.
 */
enum class UiMessage {
    RefreshComplete,
    RefreshFailed,
    TaskSaveFailed,
    ScheduleTodayFailed,
    NoteSaveFailed,
    NoteDeleteFailed,
    NoteUpdateFailed,
    SettingsSaved,
}

/** Текст сообщения в текущей локали. */
@Composable
fun UiMessage.text(): String = stringResource(
    when (this) {
        UiMessage.RefreshComplete -> Res.string.refresh_complete
        UiMessage.RefreshFailed -> Res.string.refresh_error
        UiMessage.TaskSaveFailed -> Res.string.task_save_error
        UiMessage.ScheduleTodayFailed -> Res.string.schedule_today_error
        UiMessage.NoteSaveFailed -> Res.string.note_save_error
        UiMessage.NoteDeleteFailed -> Res.string.note_delete_error
        UiMessage.NoteUpdateFailed -> Res.string.note_update_error
        UiMessage.SettingsSaved -> Res.string.settings_saved
    },
)

/**
 * Все тексты сразу: сообщение приходит из корутины, а строка ресурса достаётся
 * только в композиции — доставать её в момент показа уже поздно.
 */
@Composable
fun rememberUiMessageTexts(): Map<UiMessage, String> = UiMessage.entries.associateWith { it.text() }
