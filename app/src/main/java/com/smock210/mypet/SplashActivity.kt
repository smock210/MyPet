package com.smock210.mypet

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Экран запуска на SplashScreen API (Android 12+) — FIX_PLAN.md, 6.6.
 * На старых версиях совместимость обеспечивает библиотека core-splashscreen.
 */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        SplashScreenCompat.install(this)
        super.onCreate(savedInstanceState)

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
