package com.smock210.mypet.ui.auth.huawei

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smock210.mypet.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Вход через Huawei ID (OAuth 2.0 / OpenID Connect, web-авторизация).
 *
 * Порядок: открываем страницу авторизации в Custom Tab → браузер
 * возвращает код на [HuaweiRedirectActivity] → обмениваем код на токен →
 * запрашиваем профиль пользователя и возвращаем результат вызвавшей
 * активности.
 */
class HuaweiAuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val clientId = getString(R.string.huawei_client_id)
        val redirectUri = getString(R.string.huawei_redirect_uri)
        if (clientId.isBlank() || redirectUri.isBlank()) {
            fail(getString(R.string.huawei_not_configured))
            return
        }

        val state = UUID.randomUUID().toString()
        HuaweiOAuthBridge.reset()
        HuaweiOAuthBridge.expectedState = state

        val authorizeUrl = buildString {
            append(getString(R.string.huawei_authorize_endpoint))
            append("?client_id=").append(clientId.urlEncoded())
            append("&response_type=code")
            append("&scope=").append("openid%20profile%20email".urlEncoded())
            append("&redirect_uri=").append(redirectUri.urlEncoded())
            append("&state=").append(state.urlEncoded())
        }

        openBrowser(authorizeUrl)
        awaitCode()
    }

    private fun openBrowser(url: String) {
        val uri = Uri.parse(url)
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            customTabsIntent.launchUrl(this, uri)
        } catch (e: Exception) {
            Log.w(TAG, "Custom Tabs недоступны, открываем в браузере", e)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: Exception) {
                fail(getString(R.string.huawei_no_browser))
            }
        }
    }

    /** Ждём код авторизации, который пришлёт HuaweiRedirectActivity. */
    private fun awaitCode() {
        lifecycleScope.launch {
            val started = System.currentTimeMillis()
            while (System.currentTimeMillis() - started < TIMEOUT_MS) {
                val code = HuaweiOAuthBridge.code
                val error = HuaweiOAuthBridge.error
                if (error != null) {
                    fail(error)
                    return@launch
                }
                if (!code.isNullOrBlank()) {
                    HuaweiOAuthBridge.code = null
                    exchangeCode(code)
                    return@launch
                }
                delay(POLL_INTERVAL_MS)
            }
            fail(getString(R.string.huawei_timeout))
        }
    }

    /** Обмен кода на токен и получение профиля. */
    private fun exchangeCode(code: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val token = requestToken(code)
                    val profile = requestProfile(token)
                    profile
                }
            }
            result.fold(
                onSuccess = { profile ->
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(EXTRA_ID, profile.id)
                            .putExtra(EXTRA_NAME, profile.name)
                            .putExtra(EXTRA_EMAIL, profile.email)
                            .putExtra(EXTRA_PHOTO, profile.photoUrl)
                    )
                    finish()
                },
                onFailure = { e ->
                    Log.w(TAG, "Не удалось получить профиль Huawei", e)
                    fail(e.message ?: getString(R.string.huawei_auth_failed))
                }
            )
        }
    }

    private fun requestToken(code: String): String {
        val body = "grant_type=authorization_code" +
            "&code=" + code.urlEncoded() +
            "&client_id=" + getString(R.string.huawei_client_id).urlEncoded() +
            "&client_secret=" + getString(R.string.huawei_client_secret).urlEncoded() +
            "&redirect_uri=" + getString(R.string.huawei_redirect_uri).urlEncoded()

        val json = postForm(getString(R.string.huawei_token_endpoint), body)
        val error = json.optString("error")
        if (error.isNotBlank()) {
            error(json.optString("error_description", error))
        }
        val token = json.optString("access_token")
        if (token.isBlank()) {
            error("Сервер не вернул access_token")
        }
        return token
    }

    private fun requestProfile(accessToken: String): AuthData {
        val connection = (URL(getString(R.string.huawei_userinfo_endpoint)).openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Accept", "application/json")
            }
        try {
            val text = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(text)
            val id = json.optString("sub").ifBlank { json.optString("openid") }
            val name = json.optString("name").ifBlank {
                json.optString("display_name").ifBlank { json.optString("username") }
            }
            val email = json.optString("email").ifBlank { null }
            val photo = json.optString("picture").ifBlank {
                json.optString("photo_url").ifBlank { json.optString("avatar") }
            }
            if (id.isBlank()) {
                error("Сервер не вернул идентификатор пользователя")
            }
            return AuthData(
                id = id,
                name = name.ifBlank { email ?: id },
                email = email,
                photoUrl = photo.ifBlank { null }
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun postForm(url: String, body: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val text = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun fail(message: String) {
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message))
        finish()
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

    data class AuthData(
        val id: String,
        val name: String,
        val email: String?,
        val photoUrl: String?
    )

    companion object {
        const val EXTRA_ID = "huawei_id"
        const val EXTRA_NAME = "huawei_name"
        const val EXTRA_EMAIL = "huawei_email"
        const val EXTRA_PHOTO = "huawei_photo"
        const val EXTRA_ERROR = "huawei_error"

        private const val TAG = "HuaweiAuth"
        private const val POLL_INTERVAL_MS = 300L
        private const val TIMEOUT_MS = 5 * 60 * 1000L
    }
}
