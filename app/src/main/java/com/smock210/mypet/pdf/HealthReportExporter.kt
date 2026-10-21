package com.smock210.mypet.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.smock210.mypet.R
import com.smock210.mypet.sqlClass.Vaccination
import com.smock210.mypet.sqlClass.VetVisit
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class HealthReportExporter(private val context: Context) {

    fun generateHealthReport(
        petName: String,
        vaccinations: List<Vaccination>,
        visits: List<VetVisit>
    ): Boolean {
        return try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 16f
            }

            // Заголовок
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.report_pdf_title), canvas.width / 2f, 50f, paint)

            paint.textSize = 18f
            canvas.drawText(context.getString(R.string.report_pdf_pet, petName), canvas.width / 2f, 80f, paint)

            // Дата отчета
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            paint.textSize = 12f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(context.getString(R.string.report_pdf_date, date), 50f, 110f, paint)

            var yPosition = 150f

            // Прививки
            paint.textSize = 18f
            canvas.drawText(context.getString(R.string.report_pdf_vaccinations), 50f, yPosition, paint)
            yPosition += 30f

            if (vaccinations.isEmpty()) {
                paint.textSize = 14f
                canvas.drawText(context.getString(R.string.report_pdf_no_vaccinations), 50f, yPosition, paint)
            } else {
                vaccinations.forEach { v ->
                    val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(v.dateMillis)
                    paint.textSize = 14f
                    canvas.drawText("• ${v.name} ($dateStr)", 50f, yPosition, paint)
                    if (v.notes.isNotBlank()) {
                        yPosition += 15f
                        paint.textSize = 12f
                        canvas.drawText(context.getString(R.string.report_pdf_notes, v.notes), 50f, yPosition, paint)
                    }
                    yPosition += 25f
                }
            }

            yPosition += 20f

            // Визиты к врачу
            paint.textSize = 18f
            canvas.drawText(context.getString(R.string.report_pdf_visits), 50f, yPosition, paint)
            yPosition += 30f

            if (visits.isEmpty()) {
                paint.textSize = 14f
                canvas.drawText(context.getString(R.string.report_pdf_no_visits), 50f, yPosition, paint)
            } else {
                visits.forEach { v ->
                    val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(v.dateMillis)
                    paint.textSize = 14f
                    canvas.drawText("• ${v.clinicName} ($dateStr)", 50f, yPosition, paint)
                    if (v.diagnosis.isNotBlank()) {
                        yPosition += 15f
                        paint.textSize = 12f
                        canvas.drawText(context.getString(R.string.report_pdf_diagnosis, v.diagnosis), 50f, yPosition, paint)
                    }
                    yPosition += 25f
                }
            }

            document.finishPage(page)

            // Сохранение через MediaStore (Android 10+) или файл (legacy).
            // Важно: writeTo() нужно вызвать ДО close() — иначе отчёт пустой.
            val fileName = "pet_health_report_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
            val saved = savePdf(document, fileName)
            document.close()
            saved
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun savePdf(document: PdfDocument, fileName: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) } != null
        } else {
            @Suppress("DEPRECATION")
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { document.writeTo(it) }
            true
        }
    }
}