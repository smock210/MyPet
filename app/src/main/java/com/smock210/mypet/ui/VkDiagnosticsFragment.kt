package com.smock210.mypet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textview.MaterialTextView
import com.smock210.mypet.R
import com.smock210.mypet.utils.VkAppConfigChecker
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

        textView.text = buildLocalReport(appId, packageName, fingerprints)

        // Проверяем настройки приложения в кабинете VK через сервисный ключ
        viewLifecycleOwner.lifecycleScope.launch {
            val config = VkAppConfigChecker.check(appId.toLong())
            textView.text = buildLocalReport(appId, packageName, fingerprints) +
                    "\n\n" + buildRemoteReport(config)
        }
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
}
