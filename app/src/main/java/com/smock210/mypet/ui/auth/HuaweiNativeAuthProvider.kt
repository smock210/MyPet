package com.smock210.mypet.ui.auth

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentActivity
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParams
import com.smock210.mypet.ui.auth.AuthProviders.isHmsAvailable

/**
 * Нативный вход через Huawei ID (HMS Account Kit, модуль `com.huawei.hms:hwid`).
 *
 * Работает на устройствах с установленным HMS Core: приложение показывает
 * системный экран входа Huawei ID и получает профиль пользователя
 * (uid, имя, почта, аватар) без браузера и без своего сервера.
 *
 * Требует `agconnect-services.json` (клиентский id приложения из
 * AppGallery Connect) — файл копируется в assets задачей
 * `copyAgconnectServices`.
 */
class HuaweiNativeAuthProvider : AuthProvider(PROVIDER_ID) {

    private var pendingCallback: ((AuthResult) -> Unit)? = null

    override val title: String = "Huawei ID"

    override fun isAvailable(context: android.content.Context): Boolean =
        isHmsAvailable(context)

    /** Настройка задаётся agconnect-services.json, а не вручную. */
    override fun isConfigured(context: android.content.Context): Boolean = isHmsAvailable(context)

    override fun signIn(
        activity: FragmentActivity,
        launcher: ActivityResultLauncher<Intent>,
        onResult: (AuthResult) -> Unit
    ) {
        if (!isAvailable(activity)) {
            onResult(AuthResult.Error(activity.getString(com.smock210.mypet.R.string.huawei_not_available)))
            return
        }
        pendingCallback = onResult
        try {
            val service = HuaweiIdAuthManager.getService(
                activity,
                HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM
            )
            launcher.launch(service.getSignInIntent(REQUEST_CODE))
        } catch (e: Exception) {
            pendingCallback = null
            onResult(
                AuthResult.Error(
                    e.message ?: activity.getString(com.smock210.mypet.R.string.huawei_auth_failed)
                )
            )
        }
    }

    override fun handleActivityResult(result: ActivityResult, onResult: (AuthResult) -> Unit) {
        val callback = pendingCallback ?: onResult
        pendingCallback = null

        val data = result.data
        if (data == null) {
            callback(AuthResult.Cancelled)
            return
        }

        val task = HuaweiIdAuthManager.parseAuthResultFromIntent(data)
        task.addOnSuccessListener { auth ->
            val profile = AuthProfile(
                provider = PROVIDER_ID,
                id = auth.uid?.takeIf { it.isNotBlank() } ?: auth.openId.orEmpty(),
                name = auth.displayName.orEmpty(),
                email = auth.email?.takeIf { it.isNotBlank() },
                photoUrl = auth.avatarUri?.toString()
            )
            callback(AuthResult.Success(profile))
        }.addOnFailureListener { e ->
            callback(
                AuthResult.Error(
                    e.message ?: "Ошибка входа через Huawei ID"
                )
            )
        }
    }

    override fun signOut(context: android.content.Context) {
        // Токен хранится на стороне HMS Core, явный выход не требуется
    }

    companion object {
        const val PROVIDER_ID = "huawei"
        private const val REQUEST_CODE = "huawei_native_sign_in"
    }
}
