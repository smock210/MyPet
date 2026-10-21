package com.smock210.mypet

import android.app.Application
import android.util.Log

import com.google.android.material.color.DynamicColors
import com.vk.api.sdk.VK
import com.vk.api.sdk.VKTokenExpiredHandler

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
