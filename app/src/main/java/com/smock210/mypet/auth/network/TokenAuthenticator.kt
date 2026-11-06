package com.smock210.mypet.auth.network

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import kotlinx.coroutines.runBlocking

/**
 * Автообновление `access`-токена при `401` (ANDROID_CLIENT.md §5.5).
 *
 * - обновление выполняется строго под локом — не более одного параллельного refresh;
 * - не более одной попытки на запрос (защита от зацикливания);
 * - `401` от refresh → очистка токенов + сигнал «сессия истекла» (UI уводит на Login);
 * - `5xx`/сетевая ошибка → токены НЕ очищаются (не разлогиниваем без причины);
 * - `Authenticator` работает в отдельном потоке OkHttp, `runBlocking`
 *   главный поток не блокирует.
 *
 * @param apiRefresh Retrofit-клиент БЕЗ authenticator (иначе рекурсия)
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val apiRefresh: AuthApi,
    private val onSessionExpired: () -> Unit
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Публичные эндпоинты (login/register/refresh): их 401 — это ответ
        // на попытку входа, а не сигнал окончания сессии
        if (AuthInterceptor.isPublic(response.request.url.encodedPath)) return null

        // Не более одной попытки refresh на исходный запрос
        if (responseCount(response) >= 2) return null

        val usedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()

        // Пока ждали лок, токен мог уже обновить другой запрос — повторяем с ним
        tokenStore.access?.takeIf { it != usedToken }?.let { fresh ->
            return response.request.newBuilder()
                .header("Authorization", "Bearer $fresh")
                .build()
        }

        val refresh = tokenStore.refresh
        if (refresh.isNullOrBlank()) {
            // Токены есть, но refresh отсутствует — восстановиться нечем;
            // если токенов не было вовсе, «истекшей сессии» тоже нет
            if (tokenStore.hasSession) {
                tokenStore.clear()
                onSessionExpired()
            }
            return null
        }

        synchronized(lock) {
            tokenStore.access?.takeIf { it != usedToken }?.let { fresh ->
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $fresh")
                    .build()
            }

            val result = runBlocking {
                try {
                    apiRefresh.refresh(RefreshRequest(refresh))
                } catch (t: Throwable) {
                    null
                }
            }

            return when {
                result == null -> {
                    // Сетевая ошибка: не трогаем сессию, просто не повторяем
                    null
                }
                result.isSuccessful && result.body()?.access != null -> {
                    val newAccess = result.body()!!.access!!
                    tokenStore.access = newAccess
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccess")
                        .build()
                }
                result.code() == 401 -> {
                    // Refresh отозван/просрочен — сессия кончилась
                    tokenStore.clear()
                    onSessionExpired()
                    null
                }
                else -> {
                    // 5xx и прочее: не разлогиниваем, ошибку увидит вызывающий
                    null
                }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
