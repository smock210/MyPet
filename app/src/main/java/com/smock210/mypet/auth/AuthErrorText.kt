package com.smock210.mypet.auth

import android.content.Context
import com.smock210.mypet.R
import com.smock210.mypet.auth.network.ApiErrors
import com.smock210.mypet.auth.network.AuthApiException

/**
 * Перевод ошибок Auth API в человекочитаемый текст (ANDROID_CLIENT.md §2.13, §5.6).
 *
 * - нет соединения → «Нет соединения» (не разлогиниваем);
 * - `429` → «Слишком много попыток» (+ таймер, если сервер прислал ожидание);
 * - `5xx` → «Сервис недоступен» (не разлогиниваем);
 * - `400`/`401` → текст `error` из тела ответа.
 */
object AuthErrorText {

    fun of(context: Context, t: Throwable): String {
        val e = ApiErrors.fromThrowable(t)
        return when {
            e.isNetworkError -> context.getString(R.string.auth_err_no_connection)
            e.throttled -> {
                val seconds = e.retryAfterSeconds
                if (seconds != null) {
                    context.getString(R.string.auth_err_throttled_retry, seconds)
                } else {
                    context.getString(R.string.auth_err_throttled)
                }
            }
            e.isServerError -> context.getString(R.string.auth_err_server)
            !e.serverMessage.isNullOrBlank() -> e.serverMessage
            else -> context.getString(R.string.auth_err_unknown)
        }
    }

    /** Ошибка по полям формы: поле → сообщение сервера (для подсветки полей). */
    fun fieldErrors(t: Throwable): Map<String, String> =
        (t as? AuthApiException)?.fieldErrors.orEmpty()
}
