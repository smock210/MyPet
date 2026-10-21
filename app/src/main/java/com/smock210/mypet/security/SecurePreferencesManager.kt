package com.smock210.mypet.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferencesManager(context: Context) {

    private val sp: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sp = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String) {
        sp.edit().putString(KEY_VK_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return sp.getString(KEY_VK_TOKEN, null)
    }

    fun saveUserId(userId: Long) {
        sp.edit().putLong(KEY_VK_USER_ID, userId).apply()
    }

    fun getUserId(): Long {
        return sp.getLong(KEY_VK_USER_ID, 0L)
    }

    fun clear() {
        sp.edit().clear().apply()
    }

    // Кэш профиля VK для показа в шапке, пока данные не загружены
    fun saveProfileName(name: String) {
        sp.edit().putString(KEY_PROFILE_NAME, name).apply()
    }

    fun getProfileName(): String? = sp.getString(KEY_PROFILE_NAME, null)

    fun saveProfilePhoto(url: String) {
        sp.edit().putString(KEY_PROFILE_PHOTO, url).apply()
    }

    fun getProfilePhoto(): String? = sp.getString(KEY_PROFILE_PHOTO, null)

    companion object {
        private const val PREFS_NAME = "secure_vk_prefs"
        private const val KEY_VK_TOKEN = "vk_token"
        private const val KEY_VK_USER_ID = "vk_user_id"
        private const val KEY_PROFILE_NAME = "profile_name"
        private const val KEY_PROFILE_PHOTO = "profile_photo"
    }
}