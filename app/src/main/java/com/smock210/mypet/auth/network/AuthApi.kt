package com.smock210.mypet.auth.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Контракт Auth API (AUTH_API.md, раздел 2) — пути и тела запросов
 * зафиксированы документацией и меняться не должны.
 *
 * Все методы возвращают `Response<T>`, чтобы единообразно разбирать
 * ошибки (`400`/`401`/`429`/`5xx`) без исключений Retrofit.
 * `BASE_URL` обязана заканчиваться на "/" — см. ApiClient.
 */
interface AuthApi {

    /** Регистрация по email + паролю, сразу возвращает токены. */
    @POST("authApi/register/")
    suspend fun register(@Body body: EmailPasswordRequest): Response<RegisterResponse>

    /** Вход по email + паролю. */
    @POST("authApi/login/")
    suspend fun login(@Body body: EmailPasswordRequest): Response<TokenPair>

    /** Вход через Google (ID token от Google Sign-In / Credential Manager). */
    @POST("authApi/login/google/")
    suspend fun loginGoogle(@Body body: GoogleLoginRequest): Response<SocialLoginResponse>

    /** Вход через VK (access token из VK SDK). */
    @POST("authApi/login/vk/")
    suspend fun loginVk(@Body body: VkLoginRequest): Response<SocialLoginResponse>

    /** Обновление access по refresh (refresh не ротируется). */
    @POST("authApi/token/refresh/")
    suspend fun refresh(@Body body: RefreshRequest): Response<RefreshResponse>

    /** Отзыв refresh (blacklist). Клиент обязан удалить оба токена сам. */
    @POST("authApi/logout/")
    suspend fun logout(@Body body: LogoutRequest): Response<MessageResponse>

    /** Текущий пользователь (Authorization: Bearer). */
    @GET("authApi/me/")
    suspend fun me(): Response<UserDto>

    /** Смена пароля (Bearer). После успеха все refresh отзываются. */
    @POST("authApi/password/change/")
    suspend fun changePassword(@Body body: PasswordChangeRequest): Response<MessageResponse>

    /** Запрос кода сброса. Ответ всегда 200 (защита от перебора email). */
    @POST("authApi/password/reset/")
    suspend fun requestPasswordReset(@Body body: PasswordResetRequest): Response<MessageResponse>

    /** Установка нового пароля по коду из письма. */
    @POST("authApi/password/reset/confirm/")
    suspend fun confirmPasswordReset(@Body body: PasswordResetConfirmRequest): Response<MessageResponse>
}
