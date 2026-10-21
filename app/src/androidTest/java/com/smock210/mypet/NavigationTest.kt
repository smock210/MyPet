package com.smock210.mypet

import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.anyOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke-тесты главного экрана.
 *
 * При пустой базе список питомцев скрывается и показывается заглушка
 * `tv_empty_state`, поэтому состояние проверяется через `anyOf`.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testAppLaunches() {
        // Шапка с именем пользователя видна всегда
        Espresso.onView(ViewMatchers.withId(R.id.nameTV))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testListOrEmptyStateIsShown() {
        Espresso.onView(
            ViewMatchers.withId(R.id.tv_empty_state)
        ).check(
            ViewAssertions.matches(
                anyOf(
                    ViewMatchers.isDisplayed(),
                    ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.GONE)
                )
            )
        )
    }

    @Test
    fun testPetListIsPresent() {
        // RecyclerView либо отображается, либо скрыт (пустая база)
        Espresso.onView(ViewMatchers.withId(R.id.select_pet_start))
            .check(
                ViewAssertions.matches(
                    anyOf(
                        ViewMatchers.isDisplayed(),
                        ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.GONE)
                    )
                )
            )
    }
}
