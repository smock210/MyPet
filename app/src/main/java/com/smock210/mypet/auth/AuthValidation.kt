package com.smock210.mypet.auth

/**
 * Клиентская валидация форм входа/регистрации (ANDROID_CLIENT.md §3.3).
 *
 * Отдельный объект без Android-зависимостей — покрывается unit-тестами.
 * Сервер дополнительно валидирует сам (минимум 8 символов, «типовой» пароль),
 * но форма не должна слать заведомо неверные данные.
 */
object AuthValidation {

    enum class EmailError { REQUIRED, INVALID }
    enum class PasswordError { REQUIRED, SHORT, MISMATCH }

    // Простая, но достаточная проверка формата email
    private val EMAIL_REGEX =
        Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*\\.[A-Za-z]{2,}$")

    const val MIN_PASSWORD_LENGTH = 8

    /** null — ошибок нет. */
    fun emailError(email: String?): EmailError? = when {
        email.isNullOrBlank() -> EmailError.REQUIRED
        !EMAIL_REGEX.matches(email.trim()) -> EmailError.INVALID
        else -> null
    }

    /** null — ошибок нет. */
    fun passwordError(password: String?): PasswordError? = when {
        password.isNullOrEmpty() -> PasswordError.REQUIRED
        password.length < MIN_PASSWORD_LENGTH -> PasswordError.SHORT
        else -> null
    }

    /** Проверка повтора пароля (null — совпадает). */
    fun repeatMismatchError(password: String?, repeat: String?): PasswordError? =
        if (password != repeat) PasswordError.MISMATCH else null
}
