package com.smock210.mypet.auth.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Хранение пары JWT Auth API.
 *
 * Только `EncryptedSharedPreferences` — как требует ANDROID_CLIENT.md §0.6/§5.4:
 * ни SharedPreferences, ни BuildConfig, ни логи. Файл отдельный от
 * профильного хранилища [com.smock210.mypet.security.SecurePreferencesManager].
 *
 * @param prefs принимает готовое хранилище — в проде это зашифрованное
 *              хранилище ([createEncrypted]), в тестах — обычное
 */
class TokenStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(createEncrypted(context.applicationContext))

    var access: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) {
            prefs.edit().putString(KEY_ACCESS, value).apply()
        }

    var refresh: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) {
            prefs.edit().putString(KEY_REFRESH, value).apply()
        }

    /** Есть ли сохранённая сессия (хотя бы один токен). */
    val hasSession: Boolean
        get() = !access.isNullOrBlank() || !refresh.isNullOrBlank()

    fun savePair(access: String, refresh: String) {
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .apply()
    }

    /** Полная очистка (logout, «сессия истекла», смена пароля). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "auth_secure_prefs"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"

        private fun createEncrypted(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
    }
}
