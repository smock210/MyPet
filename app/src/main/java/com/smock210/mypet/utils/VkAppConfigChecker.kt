package com.smock210.mypet.utils

import com.smock210.mypet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Проверка настроек приложения VK через сервисный ключ.
 *
 * Помогает диагностировать ошибку «Invalid request: application is blocked»:
 * VK отклоняет OAuth-запрос с Android-устройства, если в кабинете
 * разработчика не заполнены «Имя пакета для Android» и «Сертификат для Android».
 *
 * Сервисный ключ берётся из local.properties → BuildConfig (в репозиторий не попадает).
 */
object VkAppConfigChecker {

    data class AppConfig(
        val appId: Long,
        val title: String?,
        val type: String?,
        val status: Int?,
        val androidPackage: String?,
        val androidSha1: String?,
        val androidActivity: String?,
        val error: String? = null
    ) {
        /** Заполнены ли Android-настройки приложения в кабинете VK. */
        val androidConfigured: Boolean
            get() = !androidPackage.isNullOrBlank() && !androidSha1.isNullOrBlank()
    }

    private const val API_VERSION = "5.131"

    suspend fun check(appId: Long): AppConfig = withContext(Dispatchers.IO) {
        val serviceKey = BuildConfig.VK_SERVICE_KEY
        if (serviceKey.isBlank()) {
            return@withContext AppConfig(
                appId, null, null, null, null, null, null,
                error = "Сервисный ключ не задан (VK_SERVICE_KEY в local.properties)"
            )
        }

        val url = "https://api.vk.com/method/apps.get" +
            "?app_ids=$appId" +
            "&fields=title,type,status,android" +
            "&access_token=$serviceKey" +
            "&v=$API_VERSION"

        try {
            val body = readBody(url)
            val json = JSONObject(body)
            json.optJSONObject("error")?.let { err ->
                return@withContext AppConfig(
                    appId, null, null, null, null, null, null,
                    error = "VK: ${err.optString("error_msg", "ошибка запроса")}"
                )
            }
            val item = json.optJSONArray("response")?.optJSONObject(0)
                ?: return@withContext AppConfig(
                    appId, null, null, null, null, null, null,
                    error = "Приложение не найдено"
                )

            val android = item.optJSONObject("android")
            AppConfig(
                appId = appId,
                title = item.optString("title").takeIf { it.isNotBlank() },
                type = item.optString("type").takeIf { it.isNotBlank() },
                status = if (item.has("status")) item.optInt("status") else null,
                androidPackage = android?.optString("package_name")?.takeIf { it.isNotBlank() },
                androidSha1 = android?.optString("sha1_cert")?.takeIf { it.isNotBlank() },
                androidActivity = android?.optString("main_activity")?.takeIf { it.isNotBlank() },
                error = null
            )
        } catch (e: Exception) {
            AppConfig(appId, null, null, null, null, null, null, error = e.message ?: "сетевая ошибка")
        }
    }

    private fun readBody(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            connection.disconnect()
        }
    }

    /** Человекочитаемый статус приложения VK (0 — разработка, 1 — опубликовано, 2 — заблокировано). */
    fun statusText(status: Int?): String = when (status) {
        0 -> "в режиме разработки (авторизоваться могут только разработчики)"
        1 -> "опубликовано"
        2 -> "заблокировано"
        3 -> "закрыто"
        null -> "неизвестно (проверьте под учетной записью владельца)"
        else -> status.toString()
    }

    /** Тип приложения VK. */
    fun typeText(type: String?): String = when (type) {
        "standalone" -> "Standalone (приложение)"
        "website" -> "Сайт"
        "community" -> "Сообщество"
        "miniapp" -> "Мини-приложение"
        null -> "неизвестно"
        else -> type
    }
}
