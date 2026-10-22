package com.smock210.mypet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smock210.mypet.ui.auth.huawei.HuaweiLoopbackServer
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `captures code and state from query string`() = runBlocking {
        val server = HuaweiLoopbackServer(0)
        server.start()
        try {
            request(server.actualPort, "/oauth/huawei?code=TEST_CODE&state=st_1")

            val outcome = server.await(5000)
            assertNotNull("Редирект должен быть принят", outcome)
            assertEquals("TEST_CODE", outcome?.code)
            assertEquals("st_1", outcome?.state)
            assertNull(outcome?.error)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `captures code sent in form body`() = runBlocking {
        val server = HuaweiLoopbackServer(0)
        server.start()
        try {
            val connection = (URL("http://127.0.0.1:${server.actualPort}/oauth/huawei").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            connection.outputStream.use { it.write("code=FORM_CODE&state=st_2".toByteArray()) }
            connection.inputStream.readBytes()

            val outcome = server.await(5000)
            assertNotNull(outcome)
            assertEquals("FORM_CODE", outcome?.code)
            assertEquals("st_2", outcome?.state)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `reports error from server`() = runBlocking {
        val server = HuaweiLoopbackServer(0)
        server.start()
        try {
            request(server.actualPort, "/oauth/huawei?error=access_denied&error_description=User%20denied")

            val outcome = server.await(5000)
            assertNotNull(outcome)
            assertEquals("User denied", outcome?.error)
            assertNull(outcome?.code)
        } finally {
            server.stop()
        }
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
