package com.smock210.mypet

import android.app.Application
import android.util.Log

import com.google.android.material.color.DynamicColors
import com.vk.api.sdk.VK
import com.vk.api.sdk.VKTokenExpiredHandler
import com.yandex.mapkit.MapKitFactory

class VkApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Инициализация VK SDK 4.x выполняется автоматически провайдером
        // VKInitProvider из манифеста SDK (com.vk.api.sdk.internal.VKInitProvider).
        // Вручную VK.initialize() вызывать не нужно — это пересоздаст конфиг
        // и может привести к потере сохранённого токена.
        VK.addTokenExpiredHandler(tokenTracker)

        // Material 3 Dynamic Color на Android 12+ (FIX_PLAN.md, 6.5)
        DynamicColors.applyToActivitiesIfAvailable(this)

        initMapKit()
    }

    /**
     * Инициализация Яндекс.Карт (MapKit).
     *
     * Ключ берётся из local.properties (см. app/build.gradle) и попадает в
     * BuildConfig. `setApiKey` обязан вызываться до `initialize`, а
     * `initialize` — до создания любого MapView, иначе SDK бросит
     * IllegalStateException. Пустой ключ не роняет приложение: карта просто
     * не подгрузит тайлы, об этом сообщается в лог.
     */
    private fun initMapKit() {
        val apiKey = BuildConfig.MAPKIT_API_KEY
        runCatching {
            MapKitFactory.setApiKey(apiKey)
            MapKitFactory.initialize(this)
        }.onFailure {
            Log.e(TAG, "MapKit init failed", it)
        }
        if (apiKey.isBlank()) {
            Log.w(TAG, "MAPKIT_API_KEY is empty — карта не покажет тайлы. " +
                "Заполните ключ в local.properties")
        }
    }

    private val tokenTracker = object : VKTokenExpiredHandler {
        override fun onTokenExpired() {
            // Токен истёк — пользователь должен войти заново
            Log.w(TAG, "VK access token expired")
        }
    }

    private companion object {
        const val TAG = "VkApplication"
    }
}
