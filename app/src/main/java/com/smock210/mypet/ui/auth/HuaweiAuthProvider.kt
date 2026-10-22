package com.smock210.mypet.ui.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentActivity
import com.smock210.mypet.ui.auth.huawei.HuaweiAuthActivity

/**
 * Вход через Huawei ID.
 *
 * Реализован по web-авторизации AppGallery Connect (OAuth 2.0 +
 * OpenID Connect) в браузере: так он работает на любых устройствах,
 * включая Huawei/Honor без Google Play Services, и не требует HMS SDK
 * и файла agconnect-services.json.
 *
 * Требуется регистрация приложения в AppGallery Connect:
 * «Мои приложения» → Huawei ID → Авторизация через веб →
 * указать Client ID, Client Secret и Redirect URI.
 */
class HuaweiAuthProvider : AuthProvider(PROVIDER_ID) {

    override val title: String = "Huawei ID"

    override fun isAvailable(context: Context): Boolean = true

    override fun isConfigured(context: Context): Boolean {
        return context.getString(com.smock210.mypet.R.string.huawei_client_id).isNotBlank() &&
            context.getString(com.smock210.mypet.R.string.huawei_redirect_uri).isNotBlank()
    }

    override fun signIn(
        activity: FragmentActivity,
        launcher: ActivityResultLauncher<Intent>,
        onResult: (AuthResult) -> Unit
    ) {
        if (!isConfigured(activity)) {
            onResult(
                AuthResult.Error(
                    activity.getString(com.smock210.mypet.R.string.huawei_not_configured)
                )
            )
            return
        }
        launcher.launch(Intent(activity, HuaweiAuthActivity::class.java))
    }

    override fun signOut(context: Context) {
        // Токен Huawei живёт в рамках сессии и не сохраняется приложением
    }

    /**
     * Разбор результата активности: Huawei ID возвращает профиль в extras.
     */
    override fun handleActivityResult(result: ActivityResult, onResult: (AuthResult) -> Unit) {
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            onResult(
                AuthResult.Success(
                    AuthProfile(
                        provider = PROVIDER_ID,
                        id = data.getStringExtra(HuaweiAuthActivity.EXTRA_ID).orEmpty(),
                        name = data.getStringExtra(HuaweiAuthActivity.EXTRA_NAME).orEmpty(),
                        email = data.getStringExtra(HuaweiAuthActivity.EXTRA_EMAIL),
                        photoUrl = data.getStringExtra(HuaweiAuthActivity.EXTRA_PHOTO)
                    )
                )
            )
        } else {
            val error = data?.getStringExtra(HuaweiAuthActivity.EXTRA_ERROR)
            onResult(if (error != null) AuthResult.Error(error) else AuthResult.Cancelled)
        }
    }

    companion object {
        const val PROVIDER_ID = "huawei"
    }
}
