package com.smock210.mypet.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")
class DataStoreProvider(private val context: Context) {

    companion object {
        private val KEY_VK_TOKEN = stringPreferencesKey("vk_token")
        private val KEY_VK_USER_ID = longPreferencesKey("vk_user_id")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
    }

    val vkToken: Flow<String?> = context.dataStore.data.map { it[KEY_VK_TOKEN] }

    suspend fun setVkToken(token: String) {
        context.dataStore.edit { it[KEY_VK_TOKEN] = token }
    }

    val userId: Flow<Long> = context.dataStore.data.map { it[KEY_VK_USER_ID] ?: 0L }

    suspend fun setUserId(id: Long) {
        context.dataStore.edit { it[KEY_VK_USER_ID] = id }
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    val biometricLock: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BIOMETRIC_LOCK] ?: false }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BIOMETRIC_LOCK] = enabled }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    // Для синхронного доступа (совместимость с существующим кодом)
    fun getVkTokenSync(): String? = runBlocking { vkToken.first() }
    fun getUserIdSync(): Long = runBlocking { userId.first() }
    fun getThemeModeSync(): String = runBlocking { themeMode.first() }
    fun getBiometricLockSync(): Boolean = runBlocking { biometricLock.first() }
}