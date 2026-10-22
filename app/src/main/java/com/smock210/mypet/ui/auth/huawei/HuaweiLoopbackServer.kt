package com.smock210.mypet.ui.auth.huawei

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Локальный HTTP-сервер для приёма редиректа OAuth (loopback-паттерн).
 *
 * Huawei ID (веб-авторизация) требует зарегистрированный в AppGallery
 * Connect redirect URI. Форматы `mypet://` Huawei отклоняет, а
 * `http://localhost:<порт>/...` — принимает, поэтому приложение поднимает
 * сервер на 127.0.0.1 и ловит код авторизации прямо на устройстве
 * (внешний сервер не нужен).
 *
 * Redirect URI должен быть указан в AppGallery Connect дословно, например:
 * `http://localhost:8765/oauth/huawei`.
 */
class HuaweiLoopbackServer(private val port: Int) {

    /** Результат: код авторизации либо ошибка. */
    data class Outcome(val code: String?, val error: String?, val state: String?)

    private val outcome = CompletableDeferred<Outcome>()
    private var serverSocket: ServerSocket? = null

    /** Реально занятый порт (если передан 0 — порт выбирает система). */
    val actualPort: Int get() = serverSocket?.localPort ?: port

    /** Запускает сервер и слушает один успешный редирект. */
    fun start() {
        val socket = ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        Executors.newSingleThreadExecutor().execute {
            try {
                while (!outcome.isCompleted) {
                    val client = socket.accept()
                    handle(client)
                }
            } catch (e: Exception) {
                if (!outcome.isCompleted) outcome.complete(Outcome(null, e.message, null))
            }
        }
    }

    private fun handle(client: java.net.Socket) {
        try {
            client.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
                val body = if (contentLength > 0) {
                    CharArray(contentLength).also { reader.read(it) }.concatToString()
                } else {
                    ""
                }

                val target = requestLine.split(" ").getOrNull(1) ?: return
                val query = Uri.parse("http://localhost$target").query.orEmpty()
                val params = parseParams(query) + parseParams(body)

                val code = params["code"]
                // error_description понятнее для пользователя, но не всегда есть
                val error = params["error_description"] ?: params["error"]
                val state = params["state"]

                if (!code.isNullOrBlank() || !error.isNullOrBlank()) {
                    outcome.complete(Outcome(code, error, state))
                }

                val html = if (!code.isNullOrBlank() || !error.isNullOrBlank()) {
                    "<html><body style=\"font-family:sans-serif;text-align:center;padding:40px\">" +
                        "<h2>${if (!error.isNullOrBlank()) "Ошибка авторизации" else "Авторизация выполнена"}</h2>" +
                        "<p>Вернитесь в приложение LifePet.</p></body></html>"
                } else {
                    "<html><body>OK</body></html>"
                }
                val response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${html.toByteArray().size}\r\n" +
                    "Connection: close\r\n\r\n" + html
                s.getOutputStream().use { it.write(response.toByteArray()) }
            }
        } catch (e: Exception) {
            // Ошибка отдельного соединения не должна ломать ожидание
        }
    }

    private fun parseParams(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split("&").mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val name = pair.substringBefore("=")
            val value = pair.substringAfter("=", "")
            try {
                name to URLDecoder.decode(value, "UTF-8")
            } catch (e: Exception) {
                name to value
            }
        }.toMap()
    }

    /** Ждёт редирект не дольше [timeoutMs]. */
    suspend fun await(timeoutMs: Long): Outcome? = withTimeoutOrNull(timeoutMs) { outcome.await() }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
