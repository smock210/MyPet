package com.smock210.mypet.ui.auth

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentActivity

/**
 * Провайдер внешней авторизации.
 *
 * Реализации: [GoogleAuthProvider] (Google Sign-In) и [HuaweiAuthProvider]
 * (Huawei ID через OAuth 2.0 в браузере — работает на устройствах без
 * Google Play Services). Выбор провайдера — в [AuthProviders].
 */
abstract class AuthProvider(val id: String) {

    /** Название для интерфейса. */
    abstract val title: String

    /** Доступен ли провайдер на этом устройстве (есть нужные сервисы). */
    abstract fun isAvailable(context: Context): Boolean

    /** Настроены ли учётные данные (client id и т.п.). */
    abstract fun isConfigured(context: Context): Boolean

    /**
     * Запускает вход. Результат приходит либо в [onResult], либо позже
     * через [handleActivityResult] (Google возвращает его в activity result).
     */
    abstract fun signIn(
        activity: FragmentActivity,
        launcher: ActivityResultLauncher<Intent>,
        onResult: (AuthResult) -> Unit
    )

    /**
     * Обработка результата activity.
     * Google Sign-In возвращает данные в intent — их разбирает провайдер;
     * Huawei ID возвращает профиль в extras.
     */
    open fun handleActivityResult(result: ActivityResult, onResult: (AuthResult) -> Unit) = Unit

    /** Выход из аккаунта провайдера. */
    abstract fun signOut(context: Context)
}
