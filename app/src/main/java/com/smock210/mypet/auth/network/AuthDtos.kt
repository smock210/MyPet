package com.smock210.mypet.auth.network

import com.google.gson.JsonElement

/**
 * DTO контракта Auth API (AUTH_API.md, раздел 2).
 *
 * Имена полей в JSON — snake_case, поэтому Kotlin-имена совпадают с JSON
 * и `@SerialName`/`@SerializedName` не требуется (используется Gson).
 */

// ---------- Запросы ----------

data class EmailPasswordRequest(val email: String, val password: String)

data class GoogleLoginRequest(val id_token: String)

data class VkLoginRequest(val access_token: String)

data class RefreshRequest(val refresh: String)

data class LogoutRequest(val refresh: String)

data class PasswordChangeRequest(val old_password: String, val new_password: String)

data class PasswordResetRequest(val email: String)

data class PasswordResetConfirmRequest(
    val email: String,
    val code: String,
    val new_password: String
)

// ---------- Ответы ----------

data class UserDto(
    val id: Long? = null,
    val username: String? = null,
    val email: String? = null,
    val first_name: String? = null,
    val last_name: String? = null
) {
    /** Имя для отображения: «Имя Фамилия», иначе username. */
    fun displayName(): String =
        listOfNotNull(first_name, last_name)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { username.orEmpty().ifBlank { email.orEmpty() } }
}

data class TokenPair(val refresh: String, val access: String)

data class SocialLoginResponse(
    val refresh: String? = null,
    val access: String? = null,
    val user: UserDto? = null
)

data class RegisterResponse(
    val message: String? = null,
    val refresh: String? = null,
    val access: String? = null,
    val user: UserDto? = null
)

data class RefreshResponse(val access: String? = null)

/**
 * Ответы вида `{"message": ...}` / `{"error": ...}` / `{"detail": ...}`.
 * `debug_code` присылается только при DEBUG=True на сервере (сброс пароля).
 */
data class MessageResponse(
    val message: String? = null,
    val error: String? = null,
    val detail: String? = null,
    val debug_code: String? = null,
    val details: JsonElement? = null
)
