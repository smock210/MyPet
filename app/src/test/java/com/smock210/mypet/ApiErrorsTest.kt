package com.smock210.mypet

import com.smock210.mypet.auth.network.ApiErrors
import com.smock210.mypet.auth.network.AuthApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Разбор ошибок Auth API (AUTH_API.md §2.13): 400/401 текстом из тела,
 * 429 — троттлинг с ожиданием, 5xx — «сервис недоступен», сеть — «нет соединения».
 */
class ApiErrorsTest {

    @Test
    fun `400 error body becomes human message`() {
        val e = ApiErrors.parse(400, """{"error": "Invalid credentials"}""")
        assertEquals("Invalid credentials", e.serverMessage)
        assertFalse(e.throttled)
        assertFalse(e.isServerError)
        assertFalse(e.isNetworkError)
    }

    @Test
    fun `401 without body keeps code`() {
        val e = ApiErrors.parse(401, null)
        assertEquals(401, e.httpCode)
        assertNull(e.serverMessage)
    }

    @Test
    fun `429 throttle is detected with retry seconds`() {
        val e = ApiErrors.parse(
            429,
            """{"detail": "Request was throttled. Expected available in 42 seconds."}"""
        )
        assertTrue(e.throttled)
        assertEquals(42L, e.retryAfterSeconds)
    }

    @Test
    fun `5xx is server error not logout`() {
        val e = ApiErrors.parse(503, null)
        assertTrue(e.isServerError)
        assertFalse(e.throttled)
    }

    @Test
    fun `field details are mapped`() {
        val e = ApiErrors.parse(
            400,
            """{"error": "This password is too short. It must contain at least 8 characters.",
                "details": {"password": ["This password is too short. It must contain at least 8 characters."]}}"""
        )
        assertEquals("password", e.fieldErrors?.keys?.single())
        assertTrue(e.fieldErrors!!["password"]!!.contains("too short"))
    }

    @Test
    fun `io exception is network error`() {
        val e = ApiErrors.fromThrowable(IOException("timeout"))
        assertEquals(0, e.httpCode)
        assertTrue(e.isNetworkError)
    }

    @Test
    fun `unexpected exception is not mistaken for network error`() {
        val e = ApiErrors.fromThrowable(IllegalStateException("boom"))
        assertEquals(-1, e.httpCode)
        assertFalse(e.isNetworkError)
    }

    @Test
    fun `auth api exception passes through unchanged`() {
        val original = AuthApiException(400, "User already exists")
        assertTrue(ApiErrors.fromThrowable(original) === original)
    }
}
