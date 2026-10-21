package com.smock210.mypet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.textview.MaterialTextView
import com.smock210.mypet.R
import com.vk.api.sdk.VK
import com.vk.api.sdk.utils.VKUtils

/**
 * Диагностика авторизации VK.
 *
 * Показывает App ID, имя пакета и SHA-1 отпечатки сертификата подписи —
 * именно эти значения должны быть указаны в настройках приложения VK
 * (vk.com/apps?act=manage). Ошибка «Invalid request: application is blocked»
 * означает, что на стороне VK приложение не прошло проверку
 * (не опубликовано / не совпадает пакет или отпечаток).
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

        val appId = resources.getInteger(com.vk.api.sdk.R.integer.com_vk_sdk_AppId)
        val packageName = requireContext().packageName
        val fingerprints: List<String> = runCatching {
            VKUtils.getCertificateFingerprint(requireContext(), packageName)
                ?.filterNotNull()
                .orEmpty()
        }.getOrDefault(emptyList())

        val report = buildString {
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
            appendLine()
            appendLine(getString(R.string.vk_diag_hint))
        }

        view.findViewById<MaterialTextView>(R.id.tv_vk_diagnostics).text = report
    }
}
