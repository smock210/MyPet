package com.smock210.mypet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smock210.mypet.utils.ImageStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Регрессия: в БД фото хранятся абсолютным путём, а Picasso 2.x понимает
 * только URI со схемой. Без нормализации аватар и галерея показывали
 * заглушку (баг от 09.11.2026).
 */
@RunWith(AndroidJUnit4::class)
class ImageStorageTest {

    @Test
    fun `absolute path becomes file uri`() {
        val path = "/data/user/0/com.smock210.mypet/files/photos/a.jpg"
        val uri = ImageStorage.toLoadUri(path)
        assertNotNull(uri)
        assertEquals("file", uri!!.scheme)
        assertEquals(path, uri.path)
    }

    @Test
    fun `content uri is kept as is`() {
        val source = "content://media/picker/0/com.android.providers.media.photopicker/media/123"
        val uri = ImageStorage.toLoadUri(source)
        assertEquals(source, uri!!.toString())
        assertEquals("content", uri.scheme)
    }

    @Test
    fun `file uri is not wrapped twice`() {
        val source = "file:///data/user/0/com.smock210.mypet/files/photos/a.jpg"
        val uri = ImageStorage.toLoadUri(source)
        assertEquals(source, uri.toString())
    }

    @Test
    fun `http and https urls are kept`() {
        assertEquals("https", ImageStorage.toLoadUri("https://example.com/a.jpg")?.scheme)
        assertEquals("http", ImageStorage.toLoadUri("http://example.com/a.jpg")?.scheme)
    }

    @Test
    fun `blank and null give no uri`() {
        assertNull(ImageStorage.toLoadUri(null))
        assertNull(ImageStorage.toLoadUri(""))
        assertNull(ImageStorage.toLoadUri("   "))
    }
}
