package com.smock210.mypet

import com.smock210.mypet.utils.FeedingCalculator
import com.smock210.mypet.utils.FeedingCalculator.ActivityLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedingCalculatorTest {

    private val calculator = FeedingCalculator()

    @Test
    fun `puppy gets higher portion share than adult`() {
        val puppy = calculator.calculateDailyPortion(weightKg = 10.0, ageYears = 0)
        val adult = calculator.calculateDailyPortion(weightKg = 10.0, ageYears = 5)
        assertEquals(0.5, puppy, 0.0001)
        assertEquals(0.2, adult, 0.0001)
    }

    @Test
    fun `senior gets lower portion share than adult`() {
        val senior = calculator.calculateDailyPortion(weightKg = 10.0, ageYears = 9)
        val adult = calculator.calculateDailyPortion(weightKg = 10.0, ageYears = 5)
        assertEquals(0.15, senior, 0.0001)
        assert(senior < adult)
    }

    @Test
    fun `activity level scales base portion`() {
        val base = 10.0 * 0.02
        assertEquals(base * 0.8, calculator.calculateDailyPortionWithActivity(10.0, ActivityLevel.LOW), 0.0001)
        assertEquals(base, calculator.calculateDailyPortionWithActivity(10.0, ActivityLevel.MEDIUM), 0.0001)
        assertEquals(base * 1.3, calculator.calculateDailyPortionWithActivity(10.0, ActivityLevel.HIGH), 0.0001)
    }

    @Test
    fun `dividePortion splits daily portion and rounds`() {
        // 0.6 кг / 2 приёма = 0.3 кг
        assertEquals(0.3, calculator.dividePortion(0.6, 2), 0.0001)
        // 0.5 кг / 3 приёма = 0.1666... → 0.17
        assertEquals(0.17, calculator.dividePortion(0.5, 3), 0.0001)
    }

    @Test
    fun `calories formula is linear in weight`() {
        assertEquals(370, calculator.calculateDailyCalories(10.0))
        assertEquals(70, calculator.calculateDailyCalories(0.0))
    }

    @Test
    fun `calories from portion rounds to integer`() {
        // 250 г * 350 ккал / 100 = 875
        assertEquals(875, calculator.calculateCaloriesFromPortion(250.0, 350))
        // 0.15 г * 100 / 100 = 0.15 → 0
        assertEquals(0, calculator.calculateCaloriesFromPortion(0.15, 100))
    }
}
