package com.smock210.mypet

import androidx.test.core.app.ApplicationProvider
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
 *
 * Дополнительно проверяется выбор inSampleSize при импорте большого фото.
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

    @Test
    fun `local path is not copied again`() {
        val path = "/data/user/0/com.smock210.mypet/files/photos/a.jpg"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(path, ImageStorage.persist(context, path))
    }

    @Test
    fun `sample size keeps long side close to max`() {
        assertEquals(2, ImageStorage.calculateInSampleSize(4000, 3000, 1600))
        assertEquals(2, ImageStorage.calculateInSampleSize(3200, 2400, 1600))
        assertEquals(4, ImageStorage.calculateInSampleSize(6400, 4800, 1600))
        assertEquals(1, ImageStorage.calculateInSampleSize(1600, 1200, 1600))
        assertEquals(1, ImageStorage.calculateInSampleSize(800, 600, 1600))
        assertEquals(1, ImageStorage.calculateInSampleSize(0, 0, 1600))
    }
}
