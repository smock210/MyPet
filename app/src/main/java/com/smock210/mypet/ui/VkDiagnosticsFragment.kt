package com.smock210.mypet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.smock210.mypet.R
import com.smock210.mypet.utils.VkAppConfigChecker
import com.smock210.mypet.ui.auth.AuthConfig
import com.smock210.mypet.ui.auth.AuthProviders
import com.vk.api.sdk.VK
import com.vk.api.sdk.utils.VKUtils
import kotlinx.coroutines.launch

/**
 * Диагностика авторизации VK.
 *
 * Показывает App ID, имя пакета и SHA-1 отпечатки сертификата подписи —
 * именно эти значения должны быть указаны в настройках приложения VK
 * (vk.com/apps?act=manage). Ошибка «Invalid request: application is blocked»
 * означает, что на стороне VK приложение не прошло проверку
 * (не опубликовано / не совпадает пакет или отпечаток).
 *
 * Дополнительно опрашивает API VK и показывает, заполнены ли в кабинете
 * Android-настройки приложения.
 */
class VkDiagnosticsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vk_diagnostics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val textView = view.findViewById<MaterialTextView>(R.id.tv_vk_diagnostics)
        val appId = resources.getInteger(com.vk.api.sdk.R.integer.com_vk_sdk_AppId)
        val packageName = requireContext().packageName
        val fingerprints: List<String> = runCatching {
            VKUtils.getCertificateFingerprint(requireContext(), packageName)
                ?.filterNotNull()
                .orEmpty()
        }.getOrDefault(emptyList())

        setupCredentialsFields(view)

        textView.text = buildLocalReport(appId, packageName, fingerprints) +
                "\n\n" + buildProvidersReport()

        // Проверяем настройки приложения в кабинете VK через сервисный ключ
        viewLifecycleOwner.lifecycleScope.launch {
            val config = VkAppConfigChecker.check(appId.toLong())
            textView.text = buildLocalReport(appId, packageName, fingerprints) +
                    "\n\n" + buildProvidersReport() +
                    "\n\n" + buildRemoteReport(config)
        }
    }

    /**
     * Поля учётных данных: позволяют настроить вход через Google/Huawei
     * прямо на устройстве, без пересборки приложения.
     */
    private fun setupCredentialsFields(view: View) {
        val googleId = view.findViewById<TextInputEditText>(R.id.et_google_client_id)
        val huaweiId = view.findViewById<TextInputEditText>(R.id.et_huawei_client_id)
        val huaweiSecret = view.findViewById<TextInputEditText>(R.id.et_huawei_client_secret)
        val huaweiRedirect = view.findViewById<TextInputEditText>(R.id.et_huawei_redirect_uri)

        googleId.setText(AuthConfig.googleWebClientId(requireContext()))
        huaweiId.setText(AuthConfig.huaweiClientId(requireContext()))
        huaweiSecret.setText(AuthConfig.huaweiClientSecret(requireContext()))
        huaweiRedirect.setText(AuthConfig.huaweiRedirectUri(requireContext()))

        view.findViewById<MaterialButton>(R.id.btn_save_credentials).setOnClickListener {
            AuthConfig.setGoogleWebClientId(requireContext(), googleId.text?.toString().orEmpty())
            AuthConfig.setHuaweiCredentials(
                requireContext(),
                huaweiId.text?.toString().orEmpty(),
                huaweiSecret.text?.toString().orEmpty(),
                huaweiRedirect.text?.toString().orEmpty()
            )
            Snackbar.make(view, R.string.auth_credentials_saved, Snackbar.LENGTH_SHORT).show()
            refreshReport(view)
        }
    }

    private fun refreshReport(view: View) {
        val textView = view.findViewById<MaterialTextView>(R.id.tv_vk_diagnostics)
        val appId = resources.getInteger(com.vk.api.sdk.R.integer.com_vk_sdk_AppId)
        val packageName = requireContext().packageName
        val fingerprints: List<String> = runCatching {
            VKUtils.getCertificateFingerprint(requireContext(), packageName)
                ?.filterNotNull()
                .orEmpty()
        }.getOrDefault(emptyList())
        textView.text = buildLocalReport(appId, packageName, fingerprints) +
                "\n\n" + buildProvidersReport()
    }

    private fun buildLocalReport(
        appId: Int,
        packageName: String,
        fingerprints: List<String>
    ): String = buildString {
        appendLine(getString(R.string.vk_diag_app_id, appId.toString()))
        appendLine(getString(R.string.vk_diag_package, packageName))
        appendLine()
        appendLine(getString(R.string.vk_diag_fingerprints))
        if (fingerprints.isEmpty()) {
            appendLine(getString(R.string.vk_diag_no_fingerprint))
        } else {
            fingerprints.forEach { appendLine("• $it") }
        }
        appendLine()
        appendLine(
            getString(
                R.string.vk_diag_status,
                if (VK.isLoggedIn()) getString(R.string.vk_diag_logged_in)
                else getString(R.string.vk_diag_not_logged_in)
            )
        )
    }

    private fun buildRemoteReport(config: VkAppConfigChecker.AppConfig): String = buildString {
        if (config.error != null) {
            appendLine(getString(R.string.vk_diag_remote_error, config.error))
            return@buildString
        }
        appendLine(getString(R.string.vk_diag_remote_title))
        appendLine(getString(R.string.vk_diag_remote_type, VkAppConfigChecker.typeText(config.type)))
        appendLine(
            getString(
                R.string.vk_diag_remote_status,
                VkAppConfigChecker.statusText(config.status)
            )
        )
        if (config.androidConfigured) {
            appendLine(getString(R.string.vk_diag_remote_android_ok))
            appendLine("• package: ${config.androidPackage}")
            config.androidSha1?.let { appendLine("• sha1: $it") }
            config.androidActivity?.let { appendLine("• activity: $it") }
        } else {
            appendLine(getString(R.string.vk_diag_remote_android_missing))
        }
        appendLine()
        appendLine(getString(R.string.vk_diag_hint))
    }

    /** Сведения о Google/Huawei-авторизации и о том, что доступно на устройстве. */
    private fun buildProvidersReport(): String = buildString {
        val google = AuthProviders.google()
        val huawei = AuthProviders.huawei()

        appendLine(getString(R.string.auth_diag_title))
        appendLine(
            getString(
                R.string.auth_diag_google_available,
                if (google.isAvailable(requireContext())) "да" else "нет"
            )
        )
        appendLine(
            getString(
                R.string.auth_diag_google_configured,
                if (google.isConfigured(requireContext())) "да" else "нет"
            )
        )
        appendLine(
            getString(
                R.string.auth_diag_huawei_configured,
                if (huawei.isConfigured(requireContext())) "да" else "нет"
            )
        )
        appendLine(
            getString(
                R.string.auth_diag_huawei_device,
                if (AuthProviders.isHuaweiDevice()) "да" else "нет"
            )
        )
        appendLine(
            getString(
                R.string.auth_diag_hms,
                if (AuthProviders.isHmsAvailable(requireContext())) "да" else "нет"
            )
        )
        appendLine()
        appendLine(
            getString(
                R.string.auth_recommended_provider,
                AuthProviders.recommended(requireContext()).title
            )
        )
        appendLine()
        appendLine(
            getString(
                R.string.huawei_ags_setup,
                AuthConfig.huaweiRedirectUri(requireContext()),
                AuthConfig.huaweiClientId(requireContext()).ifBlank { "—" }
            )
        )
        appendLine(
            getString(
                R.string.huawei_ags_android_setup,
                requireContext().packageName,
                certificateSha256()
            )
        )
    }

    /** SHA-256 отпечаток сертификата подписи — его нужно внести в AGC. */
    private fun certificateSha256(): String {
        val packageName = requireContext().packageName
        val signature = try {
            val pm = requireContext().packageManager
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
                .signatures?.firstOrNull()
        } catch (e: Exception) {
            null
        } ?: return "—"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
