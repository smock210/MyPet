package com.smock210.mypet.ui.auth

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.smock210.mypet.R

/**
 * Вход через Google (Google Sign-In).
 *
 * Требует OAuth-клиента типа «Веб-приложение» в Google Cloud Console:
 * его id указывается в `R.string.google_web_client_id` — именно он
 * приходит в `idToken`. Без него Google отвечает ошибкой 10
 * (DEVELOPER_ERROR), поэтому настройка проверяется заранее.
 */
class GoogleAuthProvider : AuthProvider(PROVIDER_ID) {

    private var pendingCallback: ((AuthResult) -> Unit)? = null

    override val title: String = "Google"

    override fun isAvailable(context: Context): Boolean {
        return GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    override fun isConfigured(context: Context): Boolean {
        return context.getString(R.string.google_web_client_id).isNotBlank()
    }

    override fun signIn(
        activity: FragmentActivity,
        launcher: ActivityResultLauncher<Intent>,
        onResult: (AuthResult) -> Unit
    ) {
        if (!isConfigured(activity)) {
            onResult(AuthResult.Error(activity.getString(R.string.google_not_configured)))
            return
        }
        if (!isAvailable(activity)) {
            onResult(AuthResult.Error(activity.getString(R.string.google_not_available)))
            return
        }
        pendingCallback = onResult

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(activity.getString(R.string.google_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()

        val client = GoogleSignIn.getClient(activity, options)
        launcher.launch(client.signInIntent)
    }

    override fun handleActivityResult(result: ActivityResult, onResult: (AuthResult) -> Unit) {
        val callback = pendingCallback ?: onResult
        pendingCallback = null

        val data = result.data
        if (data == null) {
            callback(AuthResult.Cancelled)
            return
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        if (!task.isSuccessful) {
            val message = task.exception?.localizedMessage
                ?: task.exception?.message
                ?: "Ошибка входа через Google"
            callback(AuthResult.Error(message))
            return
        }
        val account = task.result
        callback(
            AuthResult.Success(
                AuthProfile(
                    provider = PROVIDER_ID,
                    id = account.id ?: account.email.orEmpty(),
                    name = account.displayName ?: account.email.orEmpty(),
                    email = account.email,
                    photoUrl = account.photoUrl?.toString()
                )
            )
        )
    }

    override fun signOut(context: Context) {
        // Сброс клиента требует Activity; выход выполняется на уровне приложения
        // (см. SessionStore) — здесь только заглушка.
    }
    companion object {
        const val PROVIDER_ID = "google"
    }
}
