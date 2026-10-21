package com.smock210.mypet.utils

import java.math.BigDecimal
import java.math.RoundingMode

class FeedingCalculator {

    // Расчёт порции на основе веса животного
    // Формула: 2% от веса тела для взрослых животных
    fun calculateDailyPortion(weightKg: Double, ageYears: Int = 5): Double {
        return when {
            ageYears < 1 -> weightKg * 0.05 // Молодые - 5%
            ageYears > 8 -> weightKg * 0.015 // Пожилые - 1.5%
            else -> weightKg * 0.02 // Взрослые - 2%
        }
    }

    // Расчёт порции на основе веса и активности
    fun calculateDailyPortionWithActivity(
        weightKg: Double,
        activityLevel: ActivityLevel
    ): Double {
        val basePortion = weightKg * 0.02
        return when (activityLevel) {
            ActivityLevel.LOW -> basePortion * 0.8
            ActivityLevel.MEDIUM -> basePortion * 1.0
            ActivityLevel.HIGH -> basePortion * 1.3
        }
    }

    // Деление дневной порции на количество приёмов
    fun dividePortion(dailyPortion: Double, mealsPerDay: Int): Double {
        return roundToTwoDecimals(dailyPortion / mealsPerDay)
    }

    // Расчёт калорий на основе веса
    fun calculateDailyCalories(weightKg: Double): Int {
        return (weightKg * 30 + 70).toInt()
    }

    // Расчёт калорий на основе порции и типа корма
    fun calculateCaloriesFromPortion(portionGrams: Double, caloriesPer100g: Int): Int {
        return roundToInt(portionGrams * caloriesPer100g / 100)
    }

    private fun roundToTwoDecimals(value: Double): Double {
        return BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    private fun roundToInt(value: Double): Int {
        return BigDecimal(value).setScale(0, RoundingMode.HALF_UP).toInt()
    }

    enum class ActivityLevel {
        LOW, MEDIUM, HIGH
    }
}