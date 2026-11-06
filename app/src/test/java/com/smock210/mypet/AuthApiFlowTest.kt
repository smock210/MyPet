package com.smock210.mypet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smock210.mypet.auth.AuthRepository
import com.smock210.mypet.auth.network.AuthApi
import com.smock210.mypet.auth.network.AuthApiException
import com.smock210.mypet.auth.network.AuthInterceptor
import com.smock210.mypet.auth.network.TokenAuthenticator
import com.smock210.mypet.auth.network.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Контракт Auth API на живом HTTP-сервере в тесте (AUTH_API.md, §9.2):
 * вход/регистрация, Bearer-заголовок, автообновление токена,
 * «сессия истекла», троттлинг, выход.
 *
 * Клиент собирается так же, как в [com.smock210.mypet.auth.network.ApiClient]:
 * AuthInterceptor (Bearer) + TokenAuthenticator (refresh, без рекурсии).
 */
@RunWith(AndroidJUnit4::class)
class AuthApiFlowTest {

    private lateinit var server: MockWebServer
    private lateinit var store: TokenStore
    private lateinit var repo: AuthRepository
    private var sessionExpired = false

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val context = ApplicationProvider.getApplicationContext<Context>()
        // В тестах — обычное хранилище (AndroidKeyStore в Robolectric недоступен);
        // в приложении используется EncryptedSharedPreferences (§5.4)
        store = TokenStore(context.getSharedPreferences("test_auth_prefs", Context.MODE_PRIVATE))

        val plainApi = retrofit(OkHttpClient())
        val authedClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { store.access })
            .authenticator(
                TokenAuthenticator(store, plainApi) { sessionExpired = true }
            )
            .build()
        val api = retrofit(authedClient)
        repo = AuthRepository(context, api, store)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun retrofit(client: OkHttpClient): AuthApi = Retrofit.Builder()
        .baseUrl(server.url("/").toString())
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AuthApi::class.java)

    // ---------- Вход / регистрация ----------

    @Test
    fun `login stores token pair and sends no bearer`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"refresh":"r1","access":"a1"}""")
        )
        repo.login("user@example.com", "pass12345")

        assertTrue(store.hasSession)
        assertEquals("a1", store.access)
        assertEquals("r1", store.refresh)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.endsWith("/authApi/login/"))
        // Публичный запрос — Bearer не добавляется (§5.5)
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `wrong password returns server error text and no session`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error": "Invalid credentials"}""")
        )
        try {
            repo.login("user@example.com", "wrong")
            fail("ожидалась AuthApiException")
        } catch (e: AuthApiException) {
            assertEquals(401, e.httpCode)
            assertEquals("Invalid credentials", e.serverMessage)
        }
        assertFalse(store.hasSession)
        assertFalse(sessionExpired)
    }

    @Test
    fun `register saves tokens and returns user immediately`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody(
                    """{"message": "User registered successfully", "refresh": "rr", "access": "aa",
                        "user": {"id": 7, "username": "u@e.com", "email": "u@e.com"}}"""
                )
        )
        val user = repo.register("u@e.com", "pass12345")
        assertTrue(store.hasSession)
        assertEquals(7L, user.id)
        assertEquals("u@e.com", user.email)
    }

    @Test
    fun `already registered email surfaces server error`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"error": "User already exists"}""")
        )
        try {
            repo.register("u@e.com", "pass12345")
            fail("ожидалась AuthApiException")
        } catch (e: AuthApiException) {
            assertEquals("User already exists", e.serverMessage)
        }
        assertFalse(store.hasSession)
    }

    // ---------- Bearer и обновление токена ----------

    @Test
    fun `me sends bearer header`() = runBlocking {
        store.savePair(access = "a1", refresh = "r1")
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"id":1,"username":"u","email":"u@e.com","first_name":"","last_name":""}""")
        )
        val user = repo.me()
        assertEquals("u", user.username)
        assertEquals("Bearer a1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `expired access is refreshed once and request retried`() = runBlocking {
        store.savePair(access = "a1", refresh = "r1")
        // 1) /me/ → 401, 2) refresh → 200, 3) /me/ повторно → 200
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"x"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"access":"a2"}"""))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"id":2,"username":"v","email":"v@e.com","first_name":"","last_name":""}""")
        )

        val user = repo.me()

        assertEquals("v", user.username)
        assertEquals("a2", store.access)
        assertFalse(sessionExpired)
        assertEquals(3, server.requestCount)

        val refreshReq = server.takeRequest() // первый /me/
        assertEquals("Bearer a1", refreshReq.getHeader("Authorization"))
        val refreshCall = server.takeRequest()
        assertTrue(refreshCall.path!!.endsWith("/authApi/token/refresh/"))
        assertNull(refreshCall.getHeader("Authorization"))
        val retried = server.takeRequest()
        assertEquals("Bearer a2", retried.getHeader("Authorization"))
    }

    @Test
    fun `revoked refresh clears session and signals expiry`() = runBlocking {
        store.savePair(access = "a1", refresh = "r1")
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"x"}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Token is blacklisted"}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"x"}"""))

        try {
            repo.me()
            fail("ожидалась AuthApiException")
        } catch (e: AuthApiException) {
            assertEquals(401, e.httpCode)
        }

        assertFalse(store.hasSession)
        assertTrue(sessionExpired)
    }

    // ---------- Выход, пароли, троттлинг ----------

    @Test
    fun `logout revokes refresh and clears local tokens`() = runBlocking {
        store.savePair(access = "a1", refresh = "r1")
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"detail": "Successfully logged out"}""")
        )
        repo.logout()
        assertFalse(store.hasSession)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.endsWith("/authApi/logout/"))
        assertTrue(recorded.body.readUtf8().contains("r1"))
    }

    @Test
    fun `password change success clears session`() = runBlocking {
        store.savePair(access = "a1", refresh = "r1")
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"message": "Password changed successfully. Please sign in again."}""")
        )
        repo.changePassword("old-pass-1", "new-pass-1")
        assertFalse(store.hasSession)
    }

    @Test
    fun `password change with wrong old password keeps session`() = runBlocking {
        store.savePair(access = "a1", refresh = "r1")
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error": "Old password is incorrect"}""")
        )
        try {
            repo.changePassword("wrong-1", "new-pass-1")
            fail("ожидалась AuthApiException")
        } catch (e: AuthApiException) {
            assertEquals("Old password is incorrect", e.serverMessage)
        }
        // Сессия сохранена — разлогинивать нельзя (§9.2, п.13)
        assertTrue(store.hasSession)
    }

    @Test
    fun `reset request always succeeds and returns debug code`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"message": "If this email exists, a reset code has been sent", "debug_code": "482913"}""")
        )
        val code = repo.requestPasswordReset("user@example.com")
        assertEquals("482913", code)
    }

    @Test
    fun `throttled login is flagged as throttled not as credentials error`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setBody("""{"detail": "Request was throttled. Expected available in 42 seconds."}""")
        )
        try {
            repo.login("user@example.com", "pass12345")
            fail("ожидалась AuthApiException")
        } catch (e: AuthApiException) {
            assertTrue(e.throttled)
            assertEquals(42L, e.retryAfterSeconds)
        }
        assertFalse(store.hasSession)
    }

    @Test
    fun `vk login stores tokens from provider response`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(
                    """{"refresh":"rv","access":"av",
                        "user": {"id": 55, "username": "vk_55", "email": ""}}"""
                )
        )
        val user = repo.loginWithVk("vk-access-token")
        assertEquals(55L, user.id)
        assertTrue(store.hasSession)
        assertTrue(server.takeRequest().body.readUtf8().contains("vk-access-token"))
    }
}
