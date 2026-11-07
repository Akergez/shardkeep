package com.akergez.shardkeep.ui

/** Navigation intent produced by a home-screen widget tap. */
sealed interface AppDeepLink {
    data object Tasks : AppDeepLink
    data object Notes : AppDeepLink
    data class OpenNote(val path: String) : AppDeepLink

    /**
     * Возврат с серверной страницы подключения синхронизации: полный URI в том виде,
     * в каком его открыла страница (`…?state=…&config=tresse%3A%2F%2F…`). Проверку
     * попытки и разбор делает хост через TresseServerLink.accept, не навигация.
     */
    data class ServerConnect(val returned: String) : AppDeepLink
}
