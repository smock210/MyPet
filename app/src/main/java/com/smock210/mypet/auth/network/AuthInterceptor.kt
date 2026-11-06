package com.smock210.mypet.auth.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Добавляет `Authorization: Bearer <access>` ко всем запросам, кроме
 * публичных (ANDROID_CLIENT.md §5.5): `token/refresh/`, `login/`, `register/`.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider()
        if (isPublic(request.url.encodedPath) || token.isNullOrBlank()) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )
    }

    companion object {
        private val PUBLIC_SUFFIXES = listOf(
            "authApi/token/refresh/",
            "authApi/login/",
            "authApi/register/",
            "authApi/login/google/",
            "authApi/login/vk/"
        )

        /**
         * Публичный путь: Bearer не добавляется, а на `401` от него
         * Authenticator не обязан реагировать (иначе неудачный вход
         * выглядел бы как «сессия истекла»).
         */
        fun isPublic(path: String): Boolean = PUBLIC_SUFFIXES.any { path.endsWith(it) }
    }
}
