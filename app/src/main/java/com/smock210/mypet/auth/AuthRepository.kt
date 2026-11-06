package com.smock210.mypet.auth

import android.content.Context
import com.smock210.mypet.auth.network.ApiClient
import com.smock210.mypet.auth.network.ApiErrors
import com.smock210.mypet.auth.network.AuthApi
import com.smock210.mypet.auth.network.EmailPasswordRequest
import com.smock210.mypet.auth.network.GoogleLoginRequest
import com.smock210.mypet.auth.network.LogoutRequest
import com.smock210.mypet.auth.network.PasswordChangeRequest
import com.smock210.mypet.auth.network.PasswordResetConfirmRequest
import com.smock210.mypet.auth.network.PasswordResetRequest
import com.smock210.mypet.auth.network.RegisterResponse
import com.smock210.mypet.auth.network.TokenStore
import com.smock210.mypet.auth.network.UserDto
import com.smock210.mypet.auth.network.VkLoginRequest
import com.smock210.mypet.security.SecurePreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext

/**
 * Единственная точка работы с Auth API для UI (ANDROID_CLIENT.md §6).
 *
 * Все методы — suspend (сетевые вызовы вне main thread), при ошибке кидают
 * [com.smock210.mypet.auth.network.AuthApiException]; текст для пользователя
 * получается через [AuthErrorText.of].
 *
 * После успешного входа/регистрации пара JWT сохраняется в
 * [TokenStore] (EncryptedSharedPreferences), профиль — в кэш
 * [SecurePreferencesManager] для мгновенного показа в шапке.
 */
class AuthRepository(
    context: Context,
    private val api: AuthApi = ApiClient.api,
    private val tokenStore: TokenStore = ApiClient.tokens(context)
) {

    private val appContext = context.applicationContext

    /** Сигнал «сессия истекла» (токены уже очищены Authenticator'ом). */
    val sessionExpired: SharedFlow<Unit> get() = ApiClient.sessionExpired

    /** Есть ли сохранённая сессия. */
    fun hasSession(): Boolean = tokenStore.hasSession

    // ---------- Вход / регистрация ----------

    /** Вход по email + паролю. */
    suspend fun login(email: String, password: String) = withContext(Dispatchers.IO) {
        val response = api.login(EmailPasswordRequest(email.trim(), password))
        if (!response.isSuccessful) throw ApiErrors.from(response)
        val pair = response.body() ?: throw ApiErrors.from(response)
        tokenStore.savePair(pair.access, pair.refresh)
    }

    /** Регистрация: сервер сразу возвращает токены — повторный login не нужен. */
    suspend fun register(email: String, password: String): UserDto = withContext(Dispatchers.IO) {
        val response = api.register(EmailPasswordRequest(email.trim(), password))
        if (!response.isSuccessful) throw ApiErrors.from(response)
        val body: RegisterResponse = response.body() ?: throw ApiErrors.from(response)
        val access = body.access
        val refresh = body.refresh
        if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
            throw ApiErrors.from(response)
        }
        tokenStore.savePair(access, refresh)
        body.user ?: meInternal()
    }

    /** Вход через VK: access token из VK SDK → наш JWT. */
    suspend fun loginWithVk(accessToken: String): UserDto = withContext(Dispatchers.IO) {
        val response = api.loginVk(VkLoginRequest(accessToken))
        if (!response.isSuccessful) throw ApiErrors.from(response)
        val body = response.body() ?: throw ApiErrors.from(response)
        val access = body.access
        val refresh = body.refresh
        if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
            throw ApiErrors.from(response)
        }
        tokenStore.savePair(access, refresh)
        body.user ?: meInternal()
    }

    /**
     * Вход через Google: ID token → наш JWT.
     * Требует реального Web Client ID и одинакового `GOOGLE_CLIENT_ID`
     * на сервере, иначе сервер вернёт 401 (ANDROID_CLIENT.md §5.1).
     */
    suspend fun loginWithGoogle(idToken: String): UserDto = withContext(Dispatchers.IO) {
        val response = api.loginGoogle(GoogleLoginRequest(idToken))
        if (!response.isSuccessful) throw ApiErrors.from(response)
        val body = response.body() ?: throw ApiErrors.from(response)
        val access = body.access
        val refresh = body.refresh
        if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
            throw ApiErrors.from(response)
        }
        tokenStore.savePair(access, refresh)
        body.user ?: meInternal()
    }

    // ---------- Профиль ----------

    /** `GET /authApi/me/` (Bearer добавляет AuthInterceptor). */
    suspend fun me(): UserDto = withContext(Dispatchers.IO) { meInternal() }

    /**
     * Обновляет профиль из `/me/` и сохраняет его в локальный кэш,
     * чтобы шапка главного экрана не зависела от сети.
     *
     * Имя не перезаписывается для провайдера `vk` — там имя/фото приходят
     * из VK SDK и красивее, чем username на сервере.
     *
     * @param provider кому принадлежит вход (email/vk/google); null — уточнить из кэша
     */
    suspend fun refreshProfile(provider: String? = null): UserDto {
        val user = me()
        withContext(Dispatchers.IO) {
            cacheOps { prefs ->
                provider?.let { prefs.saveProfileProvider(it) }
                val currentProvider = provider ?: prefs.getProfileProvider()
                user.email?.takeIf { it.isNotBlank() }?.let { prefs.saveProfileEmail(it) }
                if (currentProvider != "vk") {
                    user.displayName().takeIf { it.isNotBlank() }?.let { prefs.saveProfileName(it) }
                    if (provider != null) prefs.saveProfilePhoto("")
                }
            }
        }
        return user
    }

    // ---------- Выход и пароли ----------

    /**
     * Выход: сервер отзывает refresh; клиент обязан удалить оба токена сам
     * (AUTH_API.md §logout). Ошибка сети не мешает локальному выходу.
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        val refresh = tokenStore.refresh
        if (!refresh.isNullOrBlank()) {
            try {
                api.logout(LogoutRequest(refresh))
            } catch (ignored: Exception) {
                // Сервер недоступен — выходим локально в любом случае
            }
        }
        tokenStore.clear()
        clearLocalProfile()
    }

    /**
     * Смена пароля. При `200` сервер отзывает ВСЕ refresh-токены —
     * клиент обязан очистить хранилище и увести пользователя на Login
     * (AUTH_API.md §password/change).
     */
    suspend fun changePassword(oldPassword: String, newPassword: String) =
        withContext(Dispatchers.IO) {
            val response = api.changePassword(PasswordChangeRequest(oldPassword, newPassword))
            if (!response.isSuccessful) throw ApiErrors.from(response)
            tokenStore.clear()
            clearLocalProfile()
        }

    /** Шаг 1 сброса: запрос кода. Возвращает `debug_code`, если прислал сервер (только отладка). */
    suspend fun requestPasswordReset(email: String): String? = withContext(Dispatchers.IO) {
        val response = api.requestPasswordReset(PasswordResetRequest(email.trim()))
        if (!response.isSuccessful) throw ApiErrors.from(response)
        response.body()?.debug_code
    }

    /** Шаг 2 сброса: код + новый пароль. После успеха нужно войти заново. */
    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String) =
        withContext(Dispatchers.IO) {
            val response = api.confirmPasswordReset(
                PasswordResetConfirmRequest(email.trim(), code.trim(), newPassword)
            )
            if (!response.isSuccessful) throw ApiErrors.from(response)
            tokenStore.clear()
            clearLocalProfile()
        }

    /**
     * Очищает кэш профиля в шапке (не трогает ключи учётных данных
     * в том же хранилище — `SecurePreferencesManager.clear()` стёр бы их).
     */
    private fun clearLocalProfile() {
        cacheOps { prefs ->
            prefs.saveProfileName("")
            prefs.saveProfileProvider("")
            prefs.saveProfileEmail("")
            prefs.saveProfilePhoto("")
        }
    }

    /**
     * Работа с локальным кэшем профиля: ошибка хранилища (например,
     * недоступный AndroidKeyStore на стрёмных устройствах) не должна
     * ломать сеть — кэш некритичен.
     */
    private fun cacheOps(block: (SecurePreferencesManager) -> Unit) {
        try {
            block(SecurePreferencesManager(appContext))
        } catch (t: Throwable) {
            // Локальный кэш не критичен для работы Auth API
        }
    }

    private suspend fun meInternal(): UserDto {
        val response = api.me()
        if (!response.isSuccessful) throw ApiErrors.from(response)
        return response.body() ?: throw ApiErrors.from(response)
    }
}
