package com.smock210.mypet.ui.auth.huawei

/**
 * Обмен данными между активностью, открывшей браузер (HuaweiAuthActivity),
 * и активностью-приёмником редиректа (HuaweiRedirectActivity).
 *
 * Приёмник вызывается браузером по custom-схеме и не имеет прямой
 * связи с исходной активностью, поэтому код авторизации передаётся
 * через этот объект.
 */
object HuaweiOAuthBridge {

    /** Ожидаемое значение state (защита от CSRF). */
    @Volatile
    var expectedState: String? = null

    /** Код авторизации, полученный от сервера Huawei. */
    @Volatile
    var code: String? = null

    /** Ошибка, если пользователь отказал или сервер вернул error. */
    @Volatile
    var error: String? = null

    fun reset() {
        expectedState = null
        code = null
        error = null
    }
}
