package com.smock210.mypet.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.smock210.mypet.R
import com.smock210.mypet.auth.AuthErrorText
import com.smock210.mypet.auth.AuthRepository
import com.smock210.mypet.auth.AuthValidation
import com.smock210.mypet.databinding.ActivityAuthLoginBinding
import com.smock210.mypet.requests.VKUsersCommand
import com.smock210.mypet.security.SecurePreferencesManager
import com.vk.api.sdk.VK
import com.vk.api.sdk.auth.VKAuthenticationResult
import com.vk.api.sdk.auth.VKScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Вход в аккаунт Auth API (AUTH_API.md).
 *
 * - email + пароль → `POST /authApi/login/`;
 * - VK → access token из VK SDK → `POST /authApi/login/vk/`;
 * - Google → ID token → `POST /authApi/login/google/` (нужен Web Client ID,
 *   иначе показывается понятная ошибка — §7);
 * - Huawei ID → локальный вход, вне Auth API (сервис не поддерживает Huawei);
 * - ссылки «Регистрация» и «Забыли пароль?».
 *
 * Успех → `RESULT_OK` (главный экран обновляет шапку) и закрытие.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthLoginBinding
    private lateinit var repo: AuthRepository
    private lateinit var securePrefs: SecurePreferencesManager

    private var busy = false

    /** Провайдер локального (Huawei) входа, для которого идёт авторизация. */
    private var activeProvider: AuthProvider? = null

    private val registerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            finish()
        }
    }

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken == null) {
                showError(getString(R.string.auth_google_disabled))
            } else {
                completeGoogleLogin(idToken)
            }
        } catch (e: ApiException) {
            // Отмена пользователем — молча, прочие ошибки — текстом
            if (e.statusCode != 12501 /* CANCELLED */) {
                showError(getString(R.string.auth_login_failed, e.message ?: "google"))
            }
        }
    }

    private val vkLauncher = VK.login(this) { result ->
        when (result) {
            is VKAuthenticationResult.Success -> completeVkLogin(result.token.accessToken)
            is VKAuthenticationResult.Failed -> {
                val reason = result.exception.authError
                    ?: result.exception.message
                    ?: getString(R.string.vk_login_failed)
                showError(getString(R.string.vk_login_failed_reason, reason))
            }
        }
    }

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val provider = activeProvider ?: return@registerForActivityResult
        provider.handleActivityResult(result) { authResult ->
            when (authResult) {
                is AuthResult.Success -> {
                    // Локальный вход (Huawei ID): вне Auth API — просто кэш профиля
                    saveLocalProfile(authResult.profile)
                    finishSuccess()
                }
                is AuthResult.Error -> {
                    setBusy(false)
                    showError(getString(R.string.auth_login_failed, authResult.message))
                }
                AuthResult.Cancelled -> setBusy(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = AuthRepository(this)
        securePrefs = SecurePreferencesManager(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnLogin.setOnClickListener { submitEmailPassword() }
        binding.btnRegister.setOnClickListener {
            registerLauncher.launch(Intent(this, RegisterActivity::class.java))
        }
        binding.btnForgot.setOnClickListener {
            startActivity(Intent(this, PasswordResetActivity::class.java))
        }
        binding.btnVk.setOnClickListener { vkLauncher.launch(arrayListOf(VKScope.EMAIL)) }
        binding.btnGoogle.setOnClickListener { startGoogleLogin() }
        binding.btnHuawei.setOnClickListener { startHuaweiLogin() }
    }

    // ---------- Email / пароль ----------

    private fun submitEmailPassword() {
        if (busy) return
        val email = binding.etEmail.text?.toString().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        AuthValidation.emailError(email)?.let {
            binding.tilEmail.error = when (it) {
                AuthValidation.EmailError.REQUIRED -> getString(R.string.auth_err_email_required)
                AuthValidation.EmailError.INVALID -> getString(R.string.auth_err_email_invalid)
            }
            return
        }
        AuthValidation.passwordError(password)?.let {
            binding.tilPassword.error = getString(R.string.auth_err_password_required)
            return
        }
        binding.tilEmail.error = null
        binding.tilPassword.error = null

        setBusy(true)
        lifecycleScope.launch {
            try {
                repo.login(email, password)
                repo.refreshProfile(provider = "email")
                finishSuccess()
            } catch (t: Throwable) {
                setBusy(false)
                showFieldErrors(t)
                showError(AuthErrorText.of(this@LoginActivity, t))
            }
        }
    }

    // ---------- VK ----------

    private fun completeVkLogin(accessToken: String) {
        setBusy(true)
        lifecycleScope.launch {
            try {
                repo.loginWithVk(accessToken)
                repo.refreshProfile(provider = "vk")
                // Имя/фото из VK — красивее, чем username на сервере
                val vkUser = withContext(Dispatchers.IO) {
                    runCatching { VK.executeSync(VKUsersCommand()) }.getOrNull()?.firstOrNull()
                }
                if (vkUser != null) {
                    withContext(Dispatchers.IO) {
                        val fullName = "${vkUser.firstName} ${vkUser.lastName}".trim()
                        securePrefs.saveProfileName(fullName)
                        securePrefs.saveProfilePhoto(vkUser.photo.orEmpty())
                        securePrefs.saveUserId(vkUser.id)
                    }
                }
                finishSuccess()
            } catch (t: Throwable) {
                setBusy(false)
                showError(AuthErrorText.of(this@LoginActivity, t))
            }
        }
    }

    // ---------- Google ----------

    private fun startGoogleLogin() {
        if (busy) return
        val webClientId = AuthConfig.googleWebClientId(this)
        if (webClientId.isBlank()) {
            // Понятная ошибка вместо падения (ANDROID_CLIENT.md §7)
            showError(getString(R.string.auth_google_disabled))
            return
        }
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, options)
        googleLauncher.launch(client.signInIntent)
    }

    private fun completeGoogleLogin(idToken: String) {
        setBusy(true)
        lifecycleScope.launch {
            try {
                repo.loginWithGoogle(idToken)
                repo.refreshProfile(provider = "google")
                finishSuccess()
            } catch (t: Throwable) {
                setBusy(false)
                showError(AuthErrorText.of(this@LoginActivity, t))
            }
        }
    }

    // ---------- Huawei ID (локально, вне Auth API) ----------

    private fun startHuaweiLogin() {
        if (busy) return
        val native = AuthProviders.huaweiNative()
        val provider = if (native.isAvailable(this)) native else AuthProviders.huawei()
        activeProvider = provider
        setBusy(true)
        try {
            provider.signIn(this, authLauncher) { authResult ->
                when (authResult) {
                    is AuthResult.Success -> {
                        saveLocalProfile(authResult.profile)
                        finishSuccess()
                    }
                    is AuthResult.Error -> {
                        setBusy(false)
                        showError(getString(R.string.auth_login_failed, authResult.message))
                    }
                    AuthResult.Cancelled -> setBusy(false)
                }
            }
        } catch (t: Throwable) {
            activeProvider = null
            setBusy(false)
            showError(getString(R.string.auth_login_failed, t.message ?: provider.id))
        }
    }

    private fun saveLocalProfile(profile: AuthProfile) {
        lifecycleScope.launch(Dispatchers.IO) {
            securePrefs.saveProfileProvider(profile.provider)
            securePrefs.saveProfileName(profile.name)
            securePrefs.saveProfilePhoto(profile.photoUrl.orEmpty())
            securePrefs.saveProfileEmail(profile.email.orEmpty())
        }
    }

    // ---------- Общее ----------

    private fun finishSuccess() {
        setBusy(false)
        setResult(RESULT_OK)
        finish()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.visibility = if (value) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !value
        binding.btnRegister.isEnabled = !value
        binding.btnVk.isEnabled = !value
        binding.btnGoogle.isEnabled = !value
        binding.btnHuawei.isEnabled = !value
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    /** Подсветка ошибок полей из ответа сервера (details). */
    private fun showFieldErrors(t: Throwable) {
        val fields = AuthErrorText.fieldErrors(t)
        fields["email"]?.let { binding.tilEmail.error = it }
        fields["password"]?.let { binding.tilPassword.error = it }
    }
}
