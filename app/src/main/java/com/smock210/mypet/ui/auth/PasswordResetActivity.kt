package com.smock210.mypet.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.smock210.mypet.BuildConfig
import com.smock210.mypet.R
import com.smock210.mypet.auth.AuthErrorText
import com.smock210.mypet.auth.AuthRepository
import com.smock210.mypet.auth.AuthValidation
import com.smock210.mypet.databinding.ActivityAuthPasswordResetBinding
import kotlinx.coroutines.launch

/**
 * Шаг 1 сброса пароля (AUTH_API.md /password/reset/).
 *
 * Ответ сервера всегда `200` (защита от перебора email) — одинаковый текст
 * и для существующего, и для неизвестного адреса (§2.10).
 * При DEBUG на сервере приходит `debug_code` — показывается только в
 * debug-сборке для отладки (письма на сервере пока не уходят).
 */
class PasswordResetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthPasswordResetBinding
    private lateinit var repo: AuthRepository
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthPasswordResetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = AuthRepository(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnSend.setOnClickListener { sendCode() }
    }

    private fun sendCode() {
        if (busy) return
        val email = binding.etEmail.text?.toString().orEmpty()
        AuthValidation.emailError(email)?.let {
            binding.tilEmail.error = when (it) {
                AuthValidation.EmailError.REQUIRED -> getString(R.string.auth_err_email_required)
                AuthValidation.EmailError.INVALID -> getString(R.string.auth_err_email_invalid)
            }
            return
        }
        binding.tilEmail.error = null

        setBusy(true)
        lifecycleScope.launch {
            try {
                val debugCode = repo.requestPasswordReset(email)
                setBusy(false)
                binding.tvStatus.visibility = View.VISIBLE
                val status = if (BuildConfig.DEBUG && !debugCode.isNullOrBlank()) {
                    getString(R.string.auth_reset_debug_code, debugCode)
                } else {
                    getString(R.string.auth_reset_sent)
                }
                binding.tvStatus.text = status
                Snackbar.make(binding.root, R.string.auth_reset_sent, Snackbar.LENGTH_LONG).show()

                // Переходим ко второму шагу (код + новый пароль)
                startActivity(
                    Intent(this@PasswordResetActivity, PasswordResetConfirmActivity::class.java)
                        .putExtra(PasswordResetConfirmActivity.EXTRA_EMAIL, email)
                        .putExtra(PasswordResetConfirmActivity.EXTRA_DEBUG_CODE, debugCode)
                )
                finish()
            } catch (t: Throwable) {
                setBusy(false)
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = AuthErrorText.of(this@PasswordResetActivity, t)
                Snackbar.make(binding.root, AuthErrorText.of(this@PasswordResetActivity, t), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.visibility = if (value) View.VISIBLE else View.GONE
        binding.btnSend.isEnabled = !value
    }
}
