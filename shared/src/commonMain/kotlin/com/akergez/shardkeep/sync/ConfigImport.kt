package com.akergez.shardkeep.sync

import com.akergez.shardkeep.ui.settings.SyncConfigDraft

/** Откуда взять ключ шифрования для вставленной строки `tresse://`. */
enum class ImportKey {
    /** Строка несёт свой ключ. */
    Incoming,

    /** Сервер только что создал репозиторий (`new=1`): ключ создаём здесь. */
    Generate,

    /** Это тот же репозиторий, что уже настроен: оставляем свой ключ. */
    Current,
}

/**
 * Выбор ключа для импортируемой строки — те же правила, что у библиотечного
 * `TresseServerLink.accept`, но для строки, вставленной руками: кнопка «Скопировать»
 * на странице сервера отдаёт строку без ключа, ключ сервер никогда не видит.
 *
 * null — ключа взять неоткуда: строка указывает на чужой для устройства репозиторий,
 * и молча подставить свой ключ значило бы шифровать им чужие данные.
 */
fun chooseImportKey(
    incoming: SyncConfigDraft,
    newRepository: Boolean,
    current: SyncConfigDraft?,
): ImportKey? = when {
    incoming.encryptionKey.isNotBlank() -> ImportKey.Incoming
    newRepository -> ImportKey.Generate
    current != null && current.encryptionKey.isNotBlank() && current.sameRemoteAs(incoming) -> ImportKey.Current
    else -> null
}

private fun SyncConfigDraft.sameRemoteAs(other: SyncConfigDraft): Boolean {
    if (storageType != other.storageType || repoId != other.repoId) return false
    return if (storageType == "s3") {
        s3Endpoint.normalizedUrl() == other.s3Endpoint.normalizedUrl() && s3Bucket == other.s3Bucket
    } else {
        serverUrl.normalizedUrl() == other.serverUrl.normalizedUrl()
    }
}

private fun String.normalizedUrl() = trim().trimEnd('/')

/** Сервер, который предлагать для входа: уже настроенный HTTP-сервер или вкомпилированный. */
fun connectServerFor(current: SyncConfigDraft?, buildDefault: String): String =
    current?.takeIf { it.storageType != "s3" && it.serverUrl.isNotBlank() }?.serverUrl ?: buildDefault

/** Импорт отклонён по понятной пользователю причине (а не из-за битой строки). */
class ConfigImportRefused(val failure: ServerConnectFailure) : Exception(failure.name)
