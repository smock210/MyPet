package com.smock210.mypet

import android.app.Application

import com.google.android.material.color.DynamicColors
import com.vk.api.sdk.VK
import com.vk.api.sdk.VKTokenExpiredHandler

class VkApplication: Application(){
    override fun onCreate() {
        super.onCreate()
        VK.addTokenExpiredHandler(tokenTracker)
        // Material 3 Dynamic Color на Android 12+ (FIX_PLAN.md, 6.5)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
    private val tokenTracker = object: VKTokenExpiredHandler {
        override fun onTokenExpired() {
            // token expired
        }
    }
}