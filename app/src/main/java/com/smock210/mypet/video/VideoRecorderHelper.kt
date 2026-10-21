package com.smock210.mypet.video

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.ActivityResult

/**
 * Помощник записи видео через системную камеру.
 *
 * Формирует Intent для ACTION_VIDEO_CAPTURE и разбирает результат.
 * Используется с ActivityResultLauncher (устаревший startActivityForResult
 * больше не применяется) — FIX_PLAN.md, 5.2.
 */
class VideoRecorderHelper {

    /** Intent для запуска записи видео (30 сек, высокое качество). */
    fun createIntent(): Intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_DURATION_LIMIT, VIDEO_DURATION_SECONDS)
        putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
    }

    /**
     * Возвращает Uri записанного видео, если запись прошла успешно.
     * Приложение-камера само сохраняет видео в галерею.
     */
    fun extractVideoUri(result: ActivityResult): Uri? {
        return if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
    }

    companion object {
        const val VIDEO_DURATION_SECONDS = 30
    }
}
