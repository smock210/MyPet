package com.smock210.mypet.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Копирование выбранных изображений в приватное хранилище приложения.
 *
 * URI из Photo Picker / GetContent — временные: после перезапуска приложения
 * или перезагрузки устройства доступ к ним теряется, и картинка перестаёт
 * открываться. Поэтому сохраняем копию файла во внутреннем хранилище
 * и записываем в БД путь к ней (FIX_PLAN.md, 6.9).
 *
 * При импорте изображение уменьшается до [MAX_DIMENSION] по длинной стороне
 * и пережимается: фото с камеры (4000×3000, 10+ МБ) иначе занимают много
 * памяти при декодировании и заметно тормозят список/галерею.
 */
object ImageStorage {

    private const val TAG = "ImageStorage"
    private const val DIR_PHOTOS = "photos"

    /** Максимальная длина стороны сохранённого изображения, px. */
    internal const val MAX_DIMENSION = 1600

    /** Качество JPEG при пережатии (для PNG игнорируется). */
    private const val JPEG_QUALITY = 88

    /**
     * Копирует изображение из [uri] во внутреннее хранилище, уменьшая его
     * до разумного размера.
     * @return абсолютный путь к сохранённому файлу либо null при ошибке.
     */
    fun copyToAppStorage(context: Context, uri: Uri): String? {
        val dir = File(context.filesDir, DIR_PHOTOS).apply { mkdirs() }
        return try {
            val decoded = decodeScaled(context, uri) ?: return null
            val bitmap = applyExifOrientation(context, uri, decoded) ?: decoded
            if (bitmap !== decoded) decoded.recycle()
            try {
                // Прозрачные PNG нельзя сохранять в JPEG: фон станет чёрным.
                val hasAlpha = bitmap.hasAlpha()
                val target = File(dir, UUID.randomUUID().toString() + if (hasAlpha) ".png" else ".jpg")
                FileOutputStream(target).use { output ->
                    val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    if (!bitmap.compress(format, JPEG_QUALITY, output)) {
                        target.delete()
                        return null
                    }
                }
                target.absolutePath
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось сохранить изображение $uri", e)
            null
        }
    }

    /**
     * Декодирует изображение с downsampling по границам экрана/памяти,
     * затем при необходимости точно масштабирует до [MAX_DIMENSION].
     */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        // При inJustDecodeBounds=true decodeStream всегда возвращает null —
        // это норма, а не ошибка; результат читаем из bounds.
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val longest = max(decoded.width, decoded.height)
        if (longest > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / longest
            val width = (decoded.width * scale).roundToInt().coerceAtLeast(1)
            val height = (decoded.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
            if (scaled !== decoded) decoded.recycle()
            decoded = scaled
        }
        return decoded
    }

    /**
     * Применяет EXIF-поворот к декодированному bitmap: при пережатии
     * EXIF-теги теряются, и фото с камеры иначе окажется боком.
     * Возвращает null, если ориентация нормальная или EXIF недоступен.
     */
    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap? {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: return null
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось прочитать EXIF у $uri", e)
            return null
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return null
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Во сколько раз уменьшить изображение при декодировании, чтобы его
     * длинная сторона была не меньше [maxDimension], но близко к ней.
     * Значение всегда степень двойки — требование BitmapFactory.
     */
    internal fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        return sample
    }

    /**
     * Копирует файл без обработки. Используется как запасной вариант, если
     * изображение не удалось декодировать (неизвестный формат и т.п.).
     */
    private fun copyRaw(context: Context, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, DIR_PHOTOS).apply { mkdirs() }
            val target = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось скопировать изображение $uri", e)
            null
        }
    }

    /**
     * Удаляет локальный файл фото из приватного хранилища.
     * Файлы за пределами `files/photos` не трогаем.
     */
    fun delete(context: Context, source: String?) {
        if (source.isNullOrBlank()) return
        try {
            val uri = toLoadUri(source) ?: return
            if (uri.scheme != "file") return
            val path = uri.path ?: return
            val photosDir = File(context.filesDir, DIR_PHOTOS).canonicalFile
            val file = File(path).canonicalFile
            if (file.parentFile != photosDir) return
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Не удалось удалить файл фото $file")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось удалить файл фото $source", e)
        }
    }

    /**
     * Преобразует сохранённую в БД ссылку в URI, понятный загрузчикам
     * изображений (Picasso 2.x).
     *
     * В БД аватары и фото хранятся абсолютным путём
     * (`/data/user/0/.../files/photos/xxx.jpg`). Picasso понимает только
     * URI со схемой `file://`, `content://`, `http(s)://` и на «голом»
     * абсолютном пути молча показывает placeholder, поэтому оборачиваем
     * его в `Uri.fromFile`. Относится и к старым записям в БД.
     */
    fun toLoadUri(source: String?): Uri? {
        if (source.isNullOrBlank()) return null
        val hasScheme = source.startsWith("content://") ||
            source.startsWith("file://") ||
            source.startsWith("http://") ||
            source.startsWith("https://") ||
            source.startsWith("android.resource://")
        return if (hasScheme) Uri.parse(source) else Uri.fromFile(File(source))
    }

    /**
     * Приводит ссылку на изображение к постоянной: если это content:// URI —
     * копирует файл в хранилище (с уменьшением), если это уже путь к файлу —
     * возвращает как есть.
     */
    fun persist(context: Context, source: String): String {
        if (source.isBlank()) return source
        if (!source.startsWith("content://") && !source.startsWith("file://")) {
            // Уже путь к файлу в приватном хранилище
            return source
        }
        val uri = Uri.parse(source)
        val copied = copyToAppStorage(context, uri) ?: copyRaw(context, uri)
        return copied ?: source
    }
}
