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
import com.smock210.mypet.databinding.ActivityAuthPasswordChangeBinding
import kotlinx.coroutines.launch

/**
 * Смена пароля (AUTH_API.md /password/change/).
 *
 * После `200` сервер отзывает все refresh-токены: хранилище очищается
 * автоматически, приложение уводит пользователя на экран Login (§2.9).
 */
class PasswordChangeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthPasswordChangeBinding
    private lateinit var repo: AuthRepository
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthPasswordChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = AuthRepository(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnSave.setOnClickListener { submit() }
    }

    private fun submit() {
        if (busy) return
        val oldPassword = binding.etOldPassword.text?.toString().orEmpty()
        val newPassword = binding.etNewPassword.text?.toString().orEmpty()
        val repeat = binding.etNewPasswordAgain.text?.toString().orEmpty()

        if (oldPassword.isEmpty()) {
            binding.tilOldPassword.error = getString(R.string.auth_err_password_required)
            return
        }
        AuthValidation.passwordError(newPassword)?.let {
            binding.tilNewPassword.error = when (it) {
                AuthValidation.PasswordError.REQUIRED -> getString(R.string.auth_err_password_required)
                else -> getString(R.string.auth_err_password_short)
            }
            return
        }
        AuthValidation.repeatMismatchError(newPassword, repeat)?.let {
            binding.tilNewPasswordAgain.error = getString(R.string.auth_err_password_mismatch)
            return
        }
        binding.tilOldPassword.error = null
        binding.tilNewPassword.error = null
        binding.tilNewPasswordAgain.error = null

        setBusy(true)
        lifecycleScope.launch {
            try {
                repo.changePassword(oldPassword, newPassword)
                Toast.makeText(
                    this@PasswordChangeActivity,
                    R.string.auth_password_changed,
                    Toast.LENGTH_LONG
                ).show()
                // Токены отозваны — сразу на Login (§2.9)
                goToLogin()
            } catch (t: Throwable) {
                setBusy(false)
                AuthErrorText.fieldErrors(t)["password"]?.let {
                    binding.tilNewPassword.error = it
                }
                Snackbar.make(
                    binding.root,
                    AuthErrorText.of(this@PasswordChangeActivity, t),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        // Закрываем и ProfileActivity под нами — она больше не актуальна
        setResult(RESULT_OK)
        finish()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.visibility = if (value) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !value
    }
}
