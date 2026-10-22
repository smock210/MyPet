package com.smock210.mypet.ui.auth.huawei

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.smock210.mypet.ui.auth.AuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64 as JavaBase64

/**
 * Вход через Huawei ID (OAuth 2.0 / OpenID Connect, веб-авторизация).
 *
 * Эндпоинты — согласно конфигурации провайдера
 * `https://oauth-login.cloud.huawei.com/.well-known/openid-configuration`:
 * - авторизация: `https://oauth-login.cloud.huawei.com/oauth2/v3/authorize`;
 * - токен: `https://oauth-login.cloud.huawei.com/oauth2/v3/token`
 *   (`client_secret_post`, поддерживается PKCE S256);
 * - endpoint `userinfo` у Huawei отсутствует, поэтому профиль берётся
 *   из JWT-токена `id_token` (claim'ы: sub, name, picture, email).
 *
 * Redirect URI: Huawei отклоняет custom-схемы (`mypet://` → invalid
 * redirect_uri) и принимает только зарегистрированные в AppGallery Connect
 * URL. Поэтому приложение поднимает локальный HTTP-сервер на 127.0.0.1
 * (loopback) и ловит код авторизации на устройстве — внешний сервер не
 * требуется. В AppGallery Connect нужно указать, например,
 * `http://localhost:8765/oauth/huawei`.
 */
class HuaweiAuthActivity : AppCompatActivity() {

    private var codeVerifier: String = ""
    private var loopback: HuaweiLoopbackServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val clientId = AuthConfig.huaweiClientId(this)
        val redirectUri = AuthConfig.huaweiRedirectUri(this)
        if (clientId.isBlank() || redirectUri.isBlank()) {
            fail(getString(com.smock210.mypet.R.string.huawei_not_configured))
            return
        }

        val redirect = Uri.parse(redirectUri)
        if (!redirect.scheme.equals("http", ignoreCase = true) && !redirect.scheme.equals("https", ignoreCase = true)) {
            fail(getString(com.smock210.mypet.R.string.huawei_redirect_scheme_error))
            return
        }

        val port = if (redirect.port > 0) redirect.port else DEFAULT_PORT
        val state = randomString(24)
        codeVerifier = randomString(64)
        val codeChallenge = sha256Base64Url(codeVerifier)

        val server = HuaweiLoopbackServer(port)
        loopback = server
        val started = runCatching { server.start() }.isSuccess
        if (!started) {
            fail(getString(com.smock210.mypet.R.string.huawei_loopback_failed, port))
            return
        }

        val authorizeUrl = buildString {
            append(getString(com.smock210.mypet.R.string.huawei_authorize_endpoint))
            append("?client_id=").append(clientId.urlEncoded())
            append("&response_type=code")
            append("&scope=").append("openid%20profile%20email".urlEncoded())
            append("&redirect_uri=").append(redirectUri.urlEncoded())
            append("&state=").append(state.urlEncoded())
            append("&code_challenge=").append(codeChallenge.urlEncoded())
            append("&code_challenge_method=S256")
        }

        openBrowser(authorizeUrl)
        awaitCode(state)
    }

    private fun openBrowser(url: String) {
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().apply {
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                launchUrl(this@HuaweiAuthActivity, uri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Custom Tabs недоступны, открываем в браузере", e)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: Exception) {
                fail(getString(com.smock210.mypet.R.string.huawei_no_browser))
            }
        }
    }

    /** Ждём код авторизации, который придёт на локальный сервер. */
    private fun awaitCode(expectedState: String) {
        lifecycleScope.launch {
            val outcome = loopback?.await(TIMEOUT_MS)
            if (outcome == null) {
                fail(getString(com.smock210.mypet.R.string.huawei_timeout))
                return@launch
            }
            val error = outcome.error
            if (!error.isNullOrBlank()) {
                fail(error)
                return@launch
            }
            val code = outcome.code
            if (code.isNullOrBlank()) {
                fail(getString(com.smock210.mypet.R.string.huawei_no_code))
                return@launch
            }
            if (!expectedState.isNullOrBlank() && !outcome.state.isNullOrBlank() && outcome.state != expectedState) {
                fail(getString(com.smock210.mypet.R.string.huawei_state_mismatch))
                return@launch
            }
            exchangeCode(code)
        }
    }

    /** Обмен кода на токен и получение профиля из id_token. */
    private fun exchangeCode(code: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { requestProfile(code) }
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
                    fail(e.message ?: getString(com.smock210.mypet.R.string.huawei_auth_failed))
                }
            )
        }
    }

    private fun requestProfile(code: String): AuthData {
        val body = "grant_type=authorization_code" +
            "&code=" + code.urlEncoded() +
            "&client_id=" + AuthConfig.huaweiClientId(this).urlEncoded() +
            "&client_secret=" + AuthConfig.huaweiClientSecret(this).urlEncoded() +
            "&redirect_uri=" + AuthConfig.huaweiRedirectUri(this).urlEncoded() +
            "&code_verifier=" + codeVerifier.urlEncoded()

        val json = postForm(getString(com.smock210.mypet.R.string.huawei_token_endpoint), body)

        val error = json.optString("error")
        if (error.isNotBlank()) {
            val description = json.optString("error_description")
            throw IllegalStateException(description.ifBlank { error })
        }

        val idToken = json.optString("id_token")
        if (idToken.isBlank()) {
            throw IllegalStateException("Сервер не вернул id_token")
        }

        val claims = decodeJwtPayload(idToken)
        val id = claims?.optString("sub").orEmpty()
        if (id.isBlank()) {
            throw IllegalStateException("В токене нет идентификатора пользователя")
        }
        val name = claims?.optString("name").orEmpty().ifBlank {
            claims?.optString("display_name").orEmpty().ifBlank {
                val given = claims?.optString("given_name").orEmpty()
                val family = claims?.optString("family_name").orEmpty()
                listOf(given, family).filter { it.isNotBlank() }.joinToString(" ")
            }
        }
        val email = claims?.optString("email").orEmpty().ifBlank { null }
        val photo = claims?.optString("picture").orEmpty().ifBlank { null }

        return AuthData(
            id = id,
            name = name.ifBlank { email ?: id },
            email = email,
            photoUrl = photo
        )
    }

    /** Декодирует payload JWT (вторая часть) — подпись проверяет сервер при выдаче токена. */
    private fun decodeJwtPayload(idToken: String): JSONObject? {
        return try {
            val payload = idToken.split(".").getOrNull(1) ?: return null
            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            JSONObject(String(decoded))
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось разобрать id_token", e)
            null
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

    override fun onDestroy() {
        super.onDestroy()
        loopback?.stop()
        loopback = null
    }

    private fun randomString(length: Int): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return String(Base64.encode(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            .replace('=', 'A')
            .take(length)
    }

    private fun sha256Base64Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return JavaBase64.getUrlEncoder().withoutPadding().encodeToString(digest)
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

        /** Порт локального сервера, если он не указан в redirect URI. */
        const val DEFAULT_PORT = 8765

        private const val TAG = "HuaweiAuth"
        private const val TIMEOUT_MS = 5 * 60 * 1000L
    }
}
