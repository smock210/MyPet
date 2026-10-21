package com.smock210.mypet.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.smock210.mypet.sqlClass.WeightRecord

/**
 * Простой линейный график истории взвешиваний (FIX_PLAN.md, 6.1).
 * Рисуется на Canvas без сторонних библиотек.
 */
class WeightChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var records: List<WeightRecord> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D6F")
        strokeWidth = 4f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B5E54")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDDDDD")
        strokeWidth = 1f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics
        )
    }

    private val dotRadius = 5f * resources.displayMetrics.density

    /** Обновляет данные графика. Пустой список рисуется заглушкой. */
    fun submit(records: List<WeightRecord>) {
        this.records = records
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val pad = 16f
        val w = width - pad * 2
        val h = height - pad * 2 - textPaint.textSize

        if (records.size < 2) {
            canvas.drawText(
                context.getString(com.smock210.mypet.R.string.chart_empty),
                pad,
                height / 2f,
                textPaint
            )
            return
        }

        val minW = records.minOf { it.weightKg }
        val maxW = records.maxOf { it.weightKg }
        val range = (maxW - minW).takeIf { it > 0.0 } ?: 1.0
        val minT = records.first().dateMillis
        val maxT = records.last().dateMillis
        val timeRange = (maxT - minT).takeIf { it > 0L } ?: 1L

        // Горизонтальная сетка + подписи min/max
        canvas.drawLine(pad, pad + h, pad + w, pad + h, gridPaint)
        canvas.drawText(
            String.format(
                java.util.Locale.getDefault(),
                "%.1f %s",
                minW,
                context.getString(com.smock210.mypet.R.string.kg_unit)
            ),
            pad,
            pad + h + textPaint.textSize + 4f,
            textPaint
        )
        canvas.drawText(
            String.format(
                java.util.Locale.getDefault(),
                "%.1f %s",
                maxW,
                context.getString(com.smock210.mypet.R.string.kg_unit)
            ),
            pad,
            pad + textPaint.textSize,
            textPaint
        )

        val points = records.map { r ->
            val x = pad + ((r.dateMillis - minT).toFloat() / timeRange) * w
            val y = pad + h - (((r.weightKg - minW) / range).toFloat() * h)
            android.graphics.PointF(x, y)
        }

        for (i in 0 until points.size - 1) {
            canvas.drawLine(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y, linePaint)
        }
        points.forEach { canvas.drawCircle(it.x, it.y, dotRadius, dotPaint) }
    }
}
