package com.smock210.mypet.ui.auth

/** Результат попытки входа. */
sealed class AuthResult {
    data class Success(val profile: AuthProfile) : AuthResult()
    data class Error(val message: String) : AuthResult()
    object Cancelled : AuthResult()
}
