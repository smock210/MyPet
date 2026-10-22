package com.smock210.mypet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smock210.mypet.ui.auth.huawei.HuaweiLoopbackServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * Проверка локального HTTP-сервера, принимающего редирект Huawei ID
 * (FIX_PLAN.md, 6.10). Именно он заменяет custom-схему, которую Huawei
 * не принимает.
 */
@RunWith(AndroidJUnit4::class)
class HuaweiLoopbackServerTest {

    @After
    fun tearDown() {
        HuaweiLoopbackServer.stop()
    }

    @Test
    fun `captures code and state from query string`() = runBlocking {
        assert(HuaweiLoopbackServer.start(0))
        val port = HuaweiLoopbackServer.actualPort

        request(port, "/oauth/huawei?code=TEST_CODE&state=st_1")

        val outcome = HuaweiLoopbackServer.await(5000)
        assertNotNull("Редирект должен быть принят", outcome)
        assertEquals("TEST_CODE", outcome?.code)
        assertEquals("st_1", outcome?.state)
        assertNull(outcome?.error)
    }

    @Test
    fun `captures code sent in form body`() = runBlocking {
        assert(HuaweiLoopbackServer.start(0))
        val port = HuaweiLoopbackServer.actualPort

        val connection = (URL("http://127.0.0.1:$port/oauth/huawei").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        connection.outputStream.use { it.write("code=FORM_CODE&state=st_2".toByteArray()) }
        connection.inputStream.readBytes()

        val outcome = HuaweiLoopbackServer.await(5000)
        assertNotNull(outcome)
        assertEquals("FORM_CODE", outcome?.code)
        assertEquals("st_2", outcome?.state)
    }

    @Test
    fun `reports error from server`() = runBlocking {
        assert(HuaweiLoopbackServer.start(0))
        val port = HuaweiLoopbackServer.actualPort

        request(port, "/oauth/huawei?error=access_denied&error_description=User%20denied")

        val outcome = HuaweiLoopbackServer.await(5000)
        assertNotNull(outcome)
        assertEquals("User denied", outcome?.error)
        assertNull(outcome?.code)
    }

    @Test
    fun `repeated start reuses the same port`() {
        assert(HuaweiLoopbackServer.start(0))
        val port = HuaweiLoopbackServer.actualPort
        // Повторный запуск с тем же портом не должен падать («порт занят»)
        assert(HuaweiLoopbackServer.start(port))
        assertEquals(port, HuaweiLoopbackServer.actualPort)
    }

    private fun request(port: Int, path: String) {
        val connection = (URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }
        connection.inputStream.readBytes()
        connection.disconnect()
    }
}
