package com.akergez.shardkeep

import com.akergez.shardkeep.sync.SERVER_CONNECT_TTL_MS
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Приёмник возврата с серверной страницы для десктопа: вместо регистрации схемы
 * URI в ОС (у каждой системы свой механизм, у jpackage/pinpit — свои ограничения)
 * возвратным адресом служит `http://localhost:PORT`, и браузер сам стучится
 * сюда обычным GET. Сервер страницы должен разрешить такой адрес в return_targets.
 *
 * Сознательно на голом `ServerSocket`, а не `com.sun.net.httpserver.HttpServer`:
 * упакованный jlink-рантайм модуль jdk.httpserver не включает, и подключать его
 * ради одного запроса к каждому из трёх инсталляторов — лишняя точка отказа.
 */
object DesktopConnectCallbackServer {
    const val PORT = 8765

    private const val BASE = "http://localhost:$PORT"

    private var server: ServerSocket? = null

    @Synchronized
    fun start(onReturn: (String) -> Unit): Boolean {
        stop()
        val listener = try {
            ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1")).apply {
                // Принимаем один возврат; дальше попытка протухает сама.
                soTimeout = SERVER_CONNECT_TTL_MS.toInt()
            }
        } catch (_: Exception) {
            return false
        }
        server = listener
        val thread = Thread {
            runCatching {
                val socket = listener.accept()
                socket.use { connection ->
                    val requestLine = BufferedReader(InputStreamReader(connection.getInputStream())).readLine()
                    // Браузер шлёт origin-form (`GET /?… HTTP/1.1`); прокси может
                    // дать absolute-form — оба вида приводим к полному URI.
                    val target = requestLine?.split(' ')?.getOrNull(1).orEmpty()
                    val returned = if (target.startsWith("http")) target else BASE + target
                    val body = RESPONSE.toByteArray()
                    connection.getOutputStream().use { out ->
                        out.write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/html; charset=utf-8\r\n" +
                                    "Content-Length: ${body.size}\r\n" +
                                    "Connection: close\r\n\r\n"
                                ).toByteArray(),
                        )
                        out.write(body)
                    }
                    onReturn(returned)
                }
            }
            stop()
        }
        thread.isDaemon = true
        thread.start()
        return true
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private val RESPONSE =
        "<!doctype html><html><head><meta charset=\"utf-8\"><title>Shardkeep</title></head>" +
            "<body style=\"font-family:sans-serif;text-align:center;margin-top:4em\">" +
            "<p>Настройки синхронизации переданы в Shardkeep.</p>" +
            "<p>Вернитесь в приложение — оно покажет, что подключилось.</p>" +
            "<p style=\"color:gray\">Sync settings received — you can close this tab.</p>" +
            "</body></html>"
}
