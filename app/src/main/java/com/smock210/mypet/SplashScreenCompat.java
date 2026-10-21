package com.smock210.mypet;

import android.app.Activity;

import androidx.core.splashscreen.SplashScreen;

/**
 * Обёртка над SplashScreen API (FIX_PLAN.md, 6.6).
 *
 * Библиотека androidx.core:core-splashscreen:1.0.1 скомпилирована Kotlin 2.0,
 * и Kotlin-компилятор проекта (1.9.24) не читает её Kotlin-метаданные,
 * поэтому статический метод вызывается из Java, где метаданные не нужны.
 */
public final class SplashScreenCompat {

    private SplashScreenCompat() {
    }

    /** Инсталлирует SplashScreen и возвращает его хэндл. */
    public static SplashScreen install(Activity activity) {
        return SplashScreen.installSplashScreen(activity);
    }
}
