package com.smock210.mypet.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.smock210.mypet.BuildConfig
import com.smock210.mypet.R
import com.smock210.mypet.auth.AuthErrorText
import com.smock210.mypet.auth.AuthRepository
import com.smock210.mypet.auth.AuthValidation
import com.smock210.mypet.databinding.ActivityAuthPasswordResetConfirmBinding
import kotlinx.coroutines.launch

/**
 * Шаг 2 сброса пароля (AUTH_API.md /password/reset/confirm/):
 * email + код из письма + новый пароль. После `200` все refresh отозваны —
 * приложение уводит на Login (§2.11).
 */
class PasswordResetConfirmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthPasswordResetConfirmBinding
    private lateinit var repo: AuthRepository
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthPasswordResetConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = AuthRepository(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        intent.getStringExtra(EXTRA_EMAIL)?.let { binding.etEmail.setText(it) }
        intent.getStringExtra(EXTRA_DEBUG_CODE)?.takeIf { BuildConfig.DEBUG && it.isNotBlank() }?.let {
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.text = getString(R.string.auth_reset_debug_code, it)
        }

        binding.btnConfirm.setOnClickListener { confirm() }
        binding.btnResend.setOnClickListener { resend() }
    }

    private fun confirm() {
        if (busy) return
        val email = binding.etEmail.text?.toString().orEmpty()
        val code = binding.etCode.text?.toString().orEmpty()
        val newPassword = binding.etNewPassword.text?.toString().orEmpty()

        AuthValidation.emailError(email)?.let {
            binding.tilEmail.error = when (it) {
                AuthValidation.EmailError.REQUIRED -> getString(R.string.auth_err_email_required)
                AuthValidation.EmailError.INVALID -> getString(R.string.auth_err_email_invalid)
            }
            return
        }
        if (code.isBlank()) {
            binding.tilCode.error = getString(R.string.auth_reset_no_email)
            return
        }
        AuthValidation.passwordError(newPassword)?.let {
            binding.tilNewPassword.error = when (it) {
                AuthValidation.PasswordError.REQUIRED -> getString(R.string.auth_err_password_required)
                else -> getString(R.string.auth_err_password_short)
            }
            return
        }
        binding.tilEmail.error = null
        binding.tilCode.error = null
        binding.tilNewPassword.error = null

        setBusy(true)
        lifecycleScope.launch {
            try {
                repo.confirmPasswordReset(email, code, newPassword)
                Toast.makeText(
                    this@PasswordResetConfirmActivity,
                    R.string.auth_reset_success,
                    Toast.LENGTH_LONG
                ).show()
                // Все refresh отозваны — на Login
                startActivity(Intent(this@PasswordResetConfirmActivity, LoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                setResult(RESULT_OK)
                finish()
            } catch (t: Throwable) {
                setBusy(false)
                AuthErrorText.fieldErrors(t)["password"]?.let { binding.tilNewPassword.error = it }
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = AuthErrorText.of(this@PasswordResetConfirmActivity, t)
            }
        }
    }

    /** Повторный запрос кода (инвалидирует предыдущие коды — §2.10). */
    private fun resend() {
        if (busy) return
        val email = binding.etEmail.text?.toString().orEmpty()
        AuthValidation.emailError(email)?.let {
            binding.tilEmail.error = when (it) {
                AuthValidation.EmailError.REQUIRED -> getString(R.string.auth_err_email_required)
                AuthValidation.EmailError.INVALID -> getString(R.string.auth_err_email_invalid)
            }
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            try {
                val debugCode = repo.requestPasswordReset(email)
                setBusy(false)
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = if (BuildConfig.DEBUG && !debugCode.isNullOrBlank()) {
                    getString(R.string.auth_reset_debug_code, debugCode)
                } else {
                    getString(R.string.auth_reset_sent)
                }
            } catch (t: Throwable) {
                setBusy(false)
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = AuthErrorText.of(this@PasswordResetConfirmActivity, t)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.visibility = if (value) View.VISIBLE else View.GONE
        binding.btnConfirm.isEnabled = !value
        binding.btnResend.isEnabled = !value
    }

    companion object {
        const val EXTRA_EMAIL = "email"
        const val EXTRA_DEBUG_CODE = "debug_code"
    }
}
