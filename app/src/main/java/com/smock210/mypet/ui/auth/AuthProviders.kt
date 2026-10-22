package com.smock210.mypet.ui.auth

import android.content.Context
import android.os.Build

/**
 * Выбор провайдера авторизации в зависимости от устройства.
 *
 * - На устройствах с Google Play Services (большинство) — Google;
 * - На Huawei/Honor без Google Play Services — Huawei ID;
 * - Если недоступны оба — пользователь может выбрать вручную.
 */
object AuthProviders {

    /** Провайдер «Войти через Google». */
    fun google(): AuthProvider = GoogleAuthProvider()

    /** Провайдер «Войти через Huawei ID». */
    fun huawei(): AuthProvider = HuaweiAuthProvider()

    /**
     * Рекомендуемый провайдер для текущего устройства.
     * На Huawei/Honor предпочтение отдаётся Huawei ID, даже если Google
     * Play Services формально доступны (там их обычно нет).
     */
    fun recommended(context: Context): AuthProvider {
        val huawei = huawei()
        val google = google()
        return chooseProvider(
            isHuaweiDevice = isHuaweiDevice(),
            googleAvailable = google.isAvailable(context),
            googleConfigured = google.isConfigured(context),
            huaweiConfigured = huawei.isConfigured(context)
        )
    }

    /**
     * Чистое правило выбора провайдера (вынесено для тестирования).
     *
     * 1. Устройство Huawei/Honor → Huawei ID;
     * 2. Иначе, если доступен и настроен Google → Google;
     * 3. Иначе — тот, что настроен; если настроены оба — Google.
     */
    fun chooseProvider(
        isHuaweiDevice: Boolean,
        googleAvailable: Boolean,
        googleConfigured: Boolean,
        huaweiConfigured: Boolean
    ): AuthProvider {
        if (isHuaweiDevice && huaweiConfigured) return huawei()
        if (googleAvailable && googleConfigured) return google()
        if (huaweiConfigured) return huawei()
        return google()
    }

    /** Производитель устройства — Huawei или Honor. */
    fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        return manufacturer in HUAWEI_NAMES || brand in HUAWEI_NAMES
    }

    /** Установлена ли HMS Core (мобильная экосистема Huawei). */
    fun isHmsAvailable(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(HMS_PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    private val HUAWEI_NAMES = setOf("huawei", "honor")
    private const val HMS_PACKAGE = "com.huawei.hwid"
}
