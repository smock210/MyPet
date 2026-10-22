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

    /** Провайдер «Войти через Huawei ID» (веб-авторизация через браузер). */
    fun huawei(): AuthProvider = HuaweiAuthProvider()

    /**
     * Нативный вход через HMS Account Kit (`com.huawei.hms:hwid`).
     * Доступен на устройствах с установленным HMS Core.
     */
    fun huaweiNative(): AuthProvider = HuaweiNativeAuthProvider()

    /**
     * Рекомендуемый провайдер для текущего устройства.
     * На Huawei/Honor с установленным HMS Core — нативный вход через
     * Huawei ID (без браузера); на остальных — Google, если он доступен.
     */
    fun recommended(context: Context): AuthProvider {
        val native = huaweiNative()
        val google = google()
        val huawei = huawei()

        if (isHuaweiDevice()) {
            if (native.isAvailable(context)) return native
            if (huawei.isConfigured(context)) return huawei
            return google
        }
        if (google.isAvailable(context) && google.isConfigured(context)) {
            return google
        }
        if (native.isAvailable(context)) return native
        return if (huawei.isConfigured(context)) huawei else google
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
        huaweiConfigured: Boolean,
        huaweiNativeAvailable: Boolean = false
    ): AuthProvider {
        if (isHuaweiDevice) {
            if (huaweiNativeAvailable) return huaweiNative()
            if (huaweiConfigured) return huawei()
            return google()
        }
        if (googleAvailable && googleConfigured) return google()
        if (huaweiNativeAvailable) return huaweiNative()
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
