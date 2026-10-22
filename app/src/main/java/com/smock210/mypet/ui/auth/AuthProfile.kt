package com.smock210.mypet.ui.auth

/**
 * Профиль пользователя из внешней системы авторизации.
 * @param provider идентификатор провайдера: vk / google / huawei
 */
data class AuthProfile(
    val provider: String,
    val id: String,
    val name: String,
    val email: String? = null,
    val photoUrl: String? = null
) {
    /** URL фотографии (может быть пустым). */
    fun photo(): String? = photoUrl?.takeIf { it.isNotBlank() }
}
