package com.smock210.mypet

import com.smock210.mypet.ui.auth.AuthProviders
import com.smock210.mypet.ui.auth.GoogleAuthProvider
import com.smock210.mypet.ui.auth.HuaweiAuthProvider
import com.smock210.mypet.ui.auth.HuaweiNativeAuthProvider
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Проверка выбора способа входа в зависимости от устройства
 * (FIX_PLAN.md, 6.10).
 */
class AuthProvidersTest {

    @Test
    fun `huawei device prefers huawei id`() {
        val provider = AuthProviders.chooseProvider(
            isHuaweiDevice = true,
            googleAvailable = true,
            googleConfigured = true,
            huaweiConfigured = true
        )
        assertEquals(HuaweiAuthProvider.PROVIDER_ID, provider.id)
    }

    @Test
    fun `huawei device without native hms uses huawei web flow`() {
        val provider = AuthProviders.chooseProvider(
            isHuaweiDevice = true,
            googleAvailable = true,
            googleConfigured = true,
            huaweiConfigured = true
        )
        assertEquals(HuaweiAuthProvider.PROVIDER_ID, provider.id)
    }

    @Test
    fun `huawei device with native hms prefers native sign in`() {
        val provider = AuthProviders.chooseProvider(
            isHuaweiDevice = true,
            googleAvailable = false,
            googleConfigured = false,
            huaweiConfigured = false,
            huaweiNativeAvailable = true
        )
        assertEquals(HuaweiNativeAuthProvider.PROVIDER_ID, provider.id)
    }

    @Test
    fun `device without google services uses native hms when available`() {
        val provider = AuthProviders.chooseProvider(
            isHuaweiDevice = false,
            googleAvailable = false,
            googleConfigured = true,
            huaweiConfigured = true,
            huaweiNativeAvailable = true
        )
        assertEquals(HuaweiNativeAuthProvider.PROVIDER_ID, provider.id)
    }

    @Test
    fun `regular device with google services uses google`() {
        val provider = AuthProviders.chooseProvider(
            isHuaweiDevice = false,
            googleAvailable = true,
            googleConfigured = true,
            huaweiConfigured = true
        )
        assertEquals(GoogleAuthProvider.PROVIDER_ID, provider.id)
    }

    @Test
    fun `device without google services uses huawei`() {
        val provider = AuthProviders.chooseProvider(
            isHuaweiDevice = false,
            googleAvailable = false,
            googleConfigured = true,
            huaweiConfigured = true
        )
        assertEquals(HuaweiAuthProvider.PROVIDER_ID, provider.id)
    }

    @Test
    fun `device without google services and without huawei falls back to google`() {
        val provider = AuthProviders.chooseProvider(
            isHuaweiDevice = false,
            googleAvailable = false,
            googleConfigured = false,
            huaweiConfigured = false
        )
        assertEquals(GoogleAuthProvider.PROVIDER_ID, provider.id)
    }

    @Test
    fun `google is used when it is not configured but huawei is`() {
        val provider = AuthProviders.chooseProvider(
            isHuaweiDevice = false,
            googleAvailable = true,
            googleConfigured = false,
            huaweiConfigured = true
        )
        assertEquals(HuaweiAuthProvider.PROVIDER_ID, provider.id)
    }
}
