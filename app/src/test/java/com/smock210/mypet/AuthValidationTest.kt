package com.smock210.mypet

import com.smock210.mypet.auth.AuthValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Клиентская валидация форм входа/регистрации (ANDROID_CLIENT.md §9.2, п.5).
 */
class AuthValidationTest {

    @Test
    fun `valid email passes`() {
        assertNull(AuthValidation.emailError("user@example.com"))
        assertNull(AuthValidation.emailError("  user+tag@sub.example.co.uk  "))
    }

    @Test
    fun `blank email is required`() {
        assertEquals(AuthValidation.EmailError.REQUIRED, AuthValidation.emailError(null))
        assertEquals(AuthValidation.EmailError.REQUIRED, AuthValidation.emailError(""))
        assertEquals(AuthValidation.EmailError.REQUIRED, AuthValidation.emailError("   "))
    }

    @Test
    fun `malformed email is invalid`() {
        assertEquals(AuthValidation.EmailError.INVALID, AuthValidation.emailError("user"))
        assertEquals(AuthValidation.EmailError.INVALID, AuthValidation.emailError("user@"))
        assertEquals(AuthValidation.EmailError.INVALID, AuthValidation.emailError("user@mail"))
        assertEquals(AuthValidation.EmailError.INVALID, AuthValidation.emailError("a b@x.ru"))
    }

    @Test
    fun `short password is rejected`() {
        // Сценарий приёмки: пароль "1" → ошибка «минимум 8 символов»
        assertEquals(AuthValidation.PasswordError.SHORT, AuthValidation.passwordError("1"))
        assertEquals(AuthValidation.PasswordError.SHORT, AuthValidation.passwordError("1234567"))
        assertNull(AuthValidation.passwordError("12345678"))
        assertNull(AuthValidation.passwordError("pass12345"))
    }

    @Test
    fun `missing password is required`() {
        assertEquals(AuthValidation.PasswordError.REQUIRED, AuthValidation.passwordError(null))
        assertEquals(AuthValidation.PasswordError.REQUIRED, AuthValidation.passwordError(""))
    }

    @Test
    fun `password repeat must match`() {
        assertNull(AuthValidation.repeatMismatchError("pass12345", "pass12345"))
        assertEquals(
            AuthValidation.PasswordError.MISMATCH,
            AuthValidation.repeatMismatchError("pass12345", "pass12346")
        )
    }
}
