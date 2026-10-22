package com.smock210.mypet.ui.auth

import android.content.Context
import com.smock210.mypet.BuildConfig
import com.smock210.mypet.R
import com.smock210.mypet.security.SecurePreferencesManager

/**
 * Учётные данные внешних систем авторизации.
 *
 * Значения берутся из зашифрованного хранилища (их можно задать прямо
 * в приложении — на экране «Диагностика входа»), а при их отсутствии
 * используются ресурсы (`strings.xml`).
 */
object AuthConfig {

    private fun prefs(context: Context): SecurePreferencesManager =
        SecurePreferencesManager(context.applicationContext)

    // ---------- Google ----------

    fun googleWebClientId(context: Context): String {
        val fromPrefs = prefs(context).getGoogleWebClientId()
        return fromPrefs?.takeIf { it.isNotBlank() }
            ?: BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.google_web_client_id)
    }

    fun setGoogleWebClientId(context: Context, value: String) {
        prefs(context).saveGoogleWebClientId(value.trim())
    }

    // ---------- Huawei ----------

    fun huaweiClientId(context: Context): String {
        val fromPrefs = prefs(context).getHuaweiClientId()
        return fromPrefs?.takeIf { it.isNotBlank() }
            ?: BuildConfig.HUAWEI_CLIENT_ID.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.huawei_client_id)
    }

    fun huaweiClientSecret(context: Context): String {
        val fromPrefs = prefs(context).getHuaweiClientSecret()
        return fromPrefs?.takeIf { it.isNotBlank() }
            ?: BuildConfig.HUAWEI_CLIENT_SECRET.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.huawei_client_secret)
    }

    fun huaweiRedirectUri(context: Context): String {
        val fromPrefs = prefs(context).getHuaweiRedirectUri()
        return fromPrefs?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.huawei_redirect_uri)
    }

    fun setHuaweiCredentials(context: Context, clientId: String, clientSecret: String, redirectUri: String) {
        prefs(context).saveHuaweiCredentials(
            clientId.trim(),
            clientSecret.trim(),
            redirectUri.trim()
        )
    }
}
