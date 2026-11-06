package com.smock210.mypet.auth.network

import com.google.gson.JsonParser
import retrofit2.Response
import java.io.IOException

/**
 * Ошибка запроса к Auth API.
 *
 * @param httpCode      HTTP-код; `0` — сетевая ошибка (нет соединения/таймаут)
 * @param serverMessage человекочитаемое сообщение из тела ответа (`error`/`detail`)
 * @param throttled     сервер ответил `429` (троттлинг) — разлогинивать нельзя
 * @param retryAfterSeconds ожидание из «Expected available in N seconds», если было
 * @param fieldErrors   разбивка `details` по полям формы (поле → сообщение)
 */
class AuthApiException(
    val httpCode: Int,
    val serverMessage: String? = null,
    val throttled: Boolean = false,
    val retryAfterSeconds: Long? = null,
    val fieldErrors: Map<String, String>? = null,
    cause: Throwable? = null
) : Exception(serverMessage ?: "HTTP $httpCode", cause) {

    /** Сервис недоступен (5xx) — не разлогинивает, можно повторить. */
    val isServerError: Boolean get() = httpCode in 500..599

    /** Нет соединения — не разлогинивает. */
    val isNetworkError: Boolean get() = httpCode == 0
}

/**
 * Разбор ошибок Auth API по разделу 2.13 документации:
 * `400` → текст из `error`, `429` → троттлинг, `5xx` → сервис недоступен.
 */
object ApiErrors {

    /** Ошибка из не-2xx ответа Retrofit. */
    fun from(response: Response<*>): AuthApiException {
        val code = response.code()
        val raw = try {
            response.errorBody()?.string()
        } catch (e: Exception) {
            null
        }
        return parse(code, raw)
    }

    /** Ошибка из исключения (IOException → «нет соединения»). */
    fun fromThrowable(t: Throwable): AuthApiException = when (t) {
        is AuthApiException -> t
        is IOException -> AuthApiException(0, serverMessage = t.message, cause = t)
        else -> AuthApiException(-1, serverMessage = t.message, cause = t)
    }

    /** Разбор тела ошибки: {error} | {detail} | {error, details}. */
    fun parse(code: Int, rawBody: String?): AuthApiException {
        var error: String? = null
        var details: Map<String, String>? = null
        if (!rawBody.isNullOrBlank()) {
            try {
                val obj = JsonParser.parseString(rawBody).asJsonObject
                error = obj.get("error")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: obj.get("detail")?.takeIf { it.isJsonPrimitive }?.asString
                obj.get("details")?.let { d ->
                    details = when {
                        d.isJsonObject -> d.asJsonObject.entrySet()
                            .filter { it.value.isJsonArray && it.value.asJsonArray.size() > 0 }
                            .associate { (field, value) ->
                                field to value.asJsonArray[0].asString
                            }
                        d.isJsonPrimitive -> mapOf("password" to d.asString)
                        else -> null
                    }
                }
            } catch (ignored: Exception) {
                // Тело не JSON — оставляем сообщением сам код
            }
        }
        val throttled = code == 429 || error?.contains("throttled", ignoreCase = true) == true
        return AuthApiException(
            httpCode = code,
            serverMessage = error,
            throttled = throttled,
            retryAfterSeconds = parseRetryAfter(error),
            fieldErrors = details
        )
    }

    /** «Expected available in 42 seconds» → 42. */
    private fun parseRetryAfter(message: String?): Long? {
        if (message == null) return null
        return Regex("Expected available in (\\d+) seconds").find(message)?.groupValues?.get(1)?.toLongOrNull()
    }
}
