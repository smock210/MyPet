package com.smock210.mypet.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.smock210.mypet.R
import com.smock210.mypet.auth.AuthErrorText
import com.smock210.mypet.auth.AuthRepository
import com.smock210.mypet.auth.AuthValidation
import com.smock210.mypet.databinding.ActivityAuthRegisterBinding
import kotlinx.coroutines.launch

/**
 * Регистрация по email + паролю (AUTH_API.md /register/).
 * Сервер сразу возвращает токены — отдельный login не вызывается (§2.1),
 * после успеха сразу открывается профиль (RESULT_OK → закрытие).
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthRegisterBinding
    private lateinit var repo: AuthRepository
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = AuthRepository(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnRegister.setOnClickListener { submit() }
        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
    }

    private fun submit() {
        if (busy) return
        val email = binding.etEmail.text?.toString().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val repeat = binding.etPasswordAgain.text?.toString().orEmpty()

        AuthValidation.emailError(email)?.let {
            binding.tilEmail.error = when (it) {
                AuthValidation.EmailError.REQUIRED -> getString(R.string.auth_err_email_required)
                AuthValidation.EmailError.INVALID -> getString(R.string.auth_err_email_invalid)
            }
            return
        }
        AuthValidation.passwordError(password)?.let {
            binding.tilPassword.error = when (it) {
                AuthValidation.PasswordError.REQUIRED -> getString(R.string.auth_err_password_required)
                else -> getString(R.string.auth_err_password_short)
            }
            return
        }
        AuthValidation.repeatMismatchError(password, repeat)?.let {
            binding.tilPasswordAgain.error = getString(R.string.auth_err_password_mismatch)
            return
        }
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilPasswordAgain.error = null

        setBusy(true)
        lifecycleScope.launch {
            try {
                repo.register(email, password)
                repo.refreshProfile(provider = "email")
                Toast.makeText(
                    this@RegisterActivity,
                    R.string.auth_register_success,
                    Toast.LENGTH_SHORT
                ).show()
                setResult(RESULT_OK)
                finish()
            } catch (t: Throwable) {
                setBusy(false)
                val fields = AuthErrorText.fieldErrors(t)
                fields["email"]?.let { binding.tilEmail.error = it }
                fields["password"]?.let { binding.tilPassword.error = it }
                Snackbar.make(binding.root, AuthErrorText.of(this@RegisterActivity, t), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.visibility = if (value) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !value
        binding.btnLogin.isEnabled = !value
    }
}
