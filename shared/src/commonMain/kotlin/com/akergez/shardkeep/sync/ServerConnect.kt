package com.akergez.shardkeep.sync

/**
 * Событие возврата с серверной страницы подключения. Разбором и проверкой занимается
 * библиотечный `TresseServerLink.accept`; сюда доходит уже либо готовый к сохранению
 * конфиг ([Request], ждёт подтверждения пользователя), либо причина отказа ([Failed]).
 */
sealed interface ServerConnectEvent {
    data class Request(
        val serverUrl: String,
        val repoId: String,
        val generatedKey: Boolean,
    ) : ServerConnectEvent

    data class Failed(val failure: ServerConnectFailure) : ServerConnectEvent
}

/** Экранные причины отказа; UI превращает каждую в строку. */
enum class ServerConnectFailure { LinkMismatch, NoConfig, OtherServer, NoKey, Decode }

/**
 * Срок жизни начатого подключения. Библиотечная попытка TTL не хранит, а за 30 минут
 * в браузере сессия на странице сервера обычно уже закрыта — старый возврат не должен
 * применяться.
 */
const val SERVER_CONNECT_TTL_MS: Long = 30 * 60 * 1000

/**
 * Раскладка исключений библиотечного `TresseServerLink.accept` по экранным причинам.
 * accept бросает require с человекочитаемыми сообщениями; привязка к ним есть место,
 * которое при смене текста деградирует до общей [ServerConnectFailure.Decode], а не
 * падает.
 */
fun mapServerConnectFailure(message: String?): ServerConnectFailure = when {
    message == null -> ServerConnectFailure.Decode
    "does not belong" in message -> ServerConnectFailure.LinkMismatch
    "no config" in message -> ServerConnectFailure.NoConfig
    "another server" in message -> ServerConnectFailure.OtherServer
    "encryption key" in message -> ServerConnectFailure.NoKey
    else -> ServerConnectFailure.Decode
}
