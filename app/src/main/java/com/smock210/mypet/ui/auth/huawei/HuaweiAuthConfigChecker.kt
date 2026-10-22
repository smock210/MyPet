package com.smock210.mypet.ui.auth.huawei

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Предварительная проверка настроек клиента Huawei ID.
 *
 * Huawei возвращает ошибки прямо на странице авторизации (JSON вместо формы),
 * поэтому до открытия браузера проверяем, зарегистрирован ли наш
 * redirect URI в AppGallery Connect, и показываем понятное сообщение.
 *
 * Используется внутренний endpoint проверки
 * `https://oauth-login.cloud.huawei.com/oauth2/ajax/authorizeConfirm`
 * (тот же, что вызывает сама страница авторизации).
 */
object HuaweiAuthConfigChecker {

    private const val CHECK_URL =
        "https://oauth-login.cloud.huawei.com/oauth2/ajax/authorizeConfirm"

    /**
     * @return null, если настройки корректны; иначе — описание проблемы.
     */
    suspend fun check(clientId: String, redirectUri: String, codeChallenge: String): String? =
        withContext(Dispatchers.IO) {
            if (clientId.isBlank() || redirectUri.isBlank()) return@withContext null
            try {
                val body = "client_id=" + clientId.urlEncoded() +
                    "&response_type=code" +
                    "&scope=" + "openid profile email".urlEncoded() +
                    "&redirect_uri=" + redirectUri.urlEncoded() +
                    "&state=check" +
                    "&code_challenge=" + codeChallenge.urlEncoded() +
                    "&code_challenge_method=S256"

                val connection = (URL(CHECK_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("interfaceVersion", "V3")
                    setRequestProperty("fromLoginAuth", "true")
                }
                try {
                    connection.outputStream.use { it.write(body.toByteArray()) }
                    // Huawei отвечает кодом 400, а тело с ошибкой кладёт в errorStream
                    val stream = if (connection.responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }
                    val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                    if (text.isBlank()) return@withContext null
                    val json = JSONObject(text)
                    val raw = json.optString("errorMessage")
                    if (raw.isBlank()) return@withContext null
                    val inner = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext null
                    val description = inner.optString("error_description")
                    when {
                        description.contains("not registered") ->
                            "Redirect URI не зарегистрирован в AppGallery Connect: $redirectUri"
                        description.contains("invalid redirect_uri") ->
                            "Huawei не принимает такой Redirect URI. Нужен http(s)-адрес, зарегистрированный в AppGallery Connect"
                        description.isNotBlank() -> description
                        else -> null
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                // Проверка не обязательна: при ошибке просто открываем авторизацию
                null
            }
        }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")
}
