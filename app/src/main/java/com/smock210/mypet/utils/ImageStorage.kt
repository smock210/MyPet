package com.smock210.mypet.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Копирование выбранных изображений в приватное хранилище приложения.
 *
 * URI из Photo Picker / GetContent — временные: после перезапуска приложения
 * или перезагрузки устройства доступ к ним теряется, и картинка перестаёт
 * открываться. Поэтому сохраняем копию файла во внутреннем хранилище
 * и записываем в БД путь к ней (FIX_PLAN.md, 6.9).
 */
object ImageStorage {

    private const val TAG = "ImageStorage"
    private const val DIR_PHOTOS = "photos"

    /**
     * Копирует изображение из [uri] во внутреннее хранилище.
     * @return абсолютный путь к сохранённому файлу либо null при ошибке.
     */
    fun copyToAppStorage(context: Context, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, DIR_PHOTOS).apply { mkdirs() }
            val target = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            target.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось сохранить изображение $uri", e)
            null
        }
    }

    /**
     * Приводит ссылку на изображение к постоянной: если это content:// URI —
     * копирует файл в хранилище, если это уже путь к файлу — возвращает как есть.
     */
    fun persist(context: Context, source: String): String {
        if (source.isBlank()) return source
        if (!source.startsWith("content://") && !source.startsWith("file://")) {
            // Уже путь к файлу в приватном хранилище
            return source
        }
        val uri = Uri.parse(source)
        val copied = copyToAppStorage(context, uri)
        return copied ?: source
    }
}
