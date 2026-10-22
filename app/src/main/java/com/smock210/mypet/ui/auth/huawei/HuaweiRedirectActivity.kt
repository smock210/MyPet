package com.smock210.mypet.ui.auth.huawei

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Приёмник редиректа от сервера Huawei: браузер открывает эту активность
 * по custom-схеме, она сохраняет код авторизации в [HuaweiOAuthBridge]
 * и сразу закрывается, возвращая пользователя в [HuaweiAuthActivity].
 */
class HuaweiRedirectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data: Uri? = intent?.data
        if (data == null) {
            HuaweiOAuthBridge.error = "Пустой ответ сервера авторизации"
            finish()
            return
        }

        val error = data.getQueryParameter("error")
        val description = data.getQueryParameter("error_description")
        if (!error.isNullOrBlank()) {
            HuaweiOAuthBridge.error = description ?: error
        } else {
            HuaweiOAuthBridge.code = data.getQueryParameter("code")
            if (HuaweiOAuthBridge.code.isNullOrBlank()) {
                HuaweiOAuthBridge.error = "Сервер не вернул код авторизации"
            }
        }

        finish()
    }
}
