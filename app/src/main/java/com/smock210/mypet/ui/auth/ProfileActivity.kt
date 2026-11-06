package com.smock210.mypet.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smock210.mypet.R
import com.smock210.mypet.auth.AuthErrorText
import com.smock210.mypet.auth.AuthRepository
import com.smock210.mypet.auth.network.AuthApiException
import com.smock210.mypet.auth.network.UserDto
import com.smock210.mypet.databinding.ActivityAuthProfileBinding
import com.vk.api.sdk.VK
import kotlinx.coroutines.launch

/**
 * Профиль пользователя Auth API (AUTH_API.md /me/):
 * id, username, email, имя/фамилия + «Сменить пароль» и «Выйти».
 *
 * Данные загружаются из `/authApi/me/`; при ошибке показывается текст
 * и остаются последние известные значения кэша не трогаем.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthProfileBinding
    private lateinit var repo: AuthRepository
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = AuthRepository(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnChangePassword.setOnClickListener {
            startActivity(Intent(this, PasswordChangeActivity::class.java))
        }
        binding.btnLogout.setOnClickListener { logout() }

        if (!repo.hasSession()) {
            // Сессии нет — грузить нечего
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.setText(R.string.auth_session_expired)
        } else {
            loadProfile()
        }
    }

    private fun loadProfile() {
        setBusy(true)
        lifecycleScope.launch {
            try {
                bind(repo.refreshProfile())
                binding.tvStatus.visibility = View.GONE
            } catch (t: Throwable) {
                if ((t as? AuthApiException)?.httpCode == 401) {
                    // Сессия кончилась (refresh отозван) — назад на главный
                    Toast.makeText(
                        this@ProfileActivity,
                        R.string.auth_session_expired,
                        Toast.LENGTH_LONG
                    ).show()
                    setResult(RESULT_CANCELED)
                    finish()
                    return@launch
                }
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = AuthErrorText.of(this@ProfileActivity, t)
            } finally {
                setBusy(false)
            }
        }
    }

    private fun bind(user: UserDto) {
        binding.tvUsername.text = user.username.orEmpty().ifBlank { "—" }
        binding.tvEmail.text = user.email.orEmpty().ifBlank { "—" }
        binding.tvId.text = user.id?.toString() ?: "—"
        binding.tvFirstName.text = user.first_name.orEmpty().ifBlank { "—" }
        binding.tvLastName.text = user.last_name.orEmpty().ifBlank { "—" }
    }

    private fun logout() {
        if (busy) return
        setBusy(true)
        lifecycleScope.launch {
            repo.logout()
            // Локальная сессия VK тоже должна закончиться — иначе шапка
            // на главном экране снова подтянет профиль VK
            if (VK.isLoggedIn()) {
                try {
                    VK.logout()
                } catch (ignored: Exception) {
                }
            }
            Toast.makeText(this@ProfileActivity, R.string.not_signed_in, Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.visibility = if (value) View.VISIBLE else View.GONE
        binding.btnChangePassword.isEnabled = !value
        binding.btnLogout.isEnabled = !value
    }
}
