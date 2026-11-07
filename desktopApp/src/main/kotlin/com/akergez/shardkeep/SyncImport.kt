package com.akergez.shardkeep

import com.akergez.shardkeep.sync.ConfigImportRefused
import com.akergez.shardkeep.sync.ImportKey
import com.akergez.shardkeep.sync.ServerConnectFailure
import com.akergez.shardkeep.sync.chooseImportKey
import com.akergez.shardkeep.ui.settings.SyncConfigDraft
import com.akergez.tresse.kmp.TresseConfig
import com.akergez.tresse.kmp.TresseConfigFile
import com.akergez.tresse.kmp.TresseConfigShare
import com.akergez.tresse.kmp.TresseServerLink

/**
 * Вставленный текст поверх конфига этой реплики.
 *
 * Строка `tresse://` — это только координаты и ключи хранилища: игнор-правила,
 * языки и расписание остаются здешними, иначе импорт обнулил бы их значениями по
 * умолчанию. Строка с кнопки «Скопировать» на странице сервера ключа не несёт —
 * его выбирает [chooseImportKey]: новый репозиторий (`new=1`) получает ключ,
 * созданный здесь через [newKey], тот же репозиторий сохраняет свой.
 *
 * Тело `tresse.toml` — наоборот, только политика каталога (игнор-правила и
 * языки); координаты хранилища в нём не бывает, и их оставляем как есть.
 *
 * Бросает [ConfigImportRefused] с причиной, если ключ взять неоткуда, и
 * IllegalArgumentException на нераспознанный текст.
 */
internal suspend fun importSyncConfig(
    text: String,
    current: TresseConfig,
    newKey: suspend () -> String,
): TresseConfig {
    val input = text.trim()
    if (!TresseConfigShare.isConfigString(input)) {
        val policy = TresseConfigFile.parse(input)
        return current.copy(ignorePatterns = policy.ignorePatterns, languages = policy.languages)
    }
    val incoming = TresseConfigShare.decode(input)
    val key = when (
        chooseImportKey(incoming.remoteDraft(), TresseServerLink.isNewRepository(input), current.remoteDraft())
    ) {
        ImportKey.Incoming -> incoming.encryptionKey
        ImportKey.Generate -> newKey()
        ImportKey.Current -> current.encryptionKey
        null -> throw ConfigImportRefused(ServerConnectFailure.NoKey)
    }
    return incoming.copy(
        encryptionKey = key,
        languages = current.languages,
        ignorePatterns = current.ignorePatterns,
        autoSyncEnabled = current.autoSyncEnabled,
        autoSyncIntervalMinutes = current.autoSyncIntervalMinutes,
        autoSyncDebounceSeconds = current.autoSyncDebounceSeconds,
    )
}

/** Шарящаяся строка — только когда синк настроен полностью, иначе экспортировать нечего. */
internal fun TresseConfig.shareStringOrNull(): String? = takeIf { it.isComplete() }?.let(TresseConfigShare::encode)

private fun TresseConfig.remoteDraft() = SyncConfigDraft(
    serverUrl = serverUrl,
    repoId = repoId,
    token = token,
    encryptionKey = encryptionKey,
    storageType = storageType,
    s3Endpoint = s3Endpoint,
    s3Bucket = s3Bucket,
)
