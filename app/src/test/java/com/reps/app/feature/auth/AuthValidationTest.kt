package com.reps.app.feature.auth

import com.reps.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {

    @Test
    fun `ordinary addresses are accepted`() {
        listOf(
            "alex@reps.app",
            "alex.rivera@reps.co.uk",
            "alex+gym@reps.app",
            "a@b.co",
            "ALEX@REPS.APP",
        ).forEach { assertNull("$it should be valid", AuthValidation.emailError(it)) }
    }

    @Test
    fun `malformed addresses are rejected`() {
        listOf("alex", "alex@", "@reps.app", "alex@reps", "alex @reps.app", "alex@@reps.app")
            .forEach {
                assertEquals(
                    "$it should be invalid",
                    R.string.error_email_invalid,
                    AuthValidation.emailError(it),
                )
            }
    }

    @Test
    fun `blank email reports empty rather than invalid`() {
        // Two different messages: "required" is more helpful than "malformed"
        // when the user simply has not typed anything yet.
        assertEquals(R.string.error_email_empty, AuthValidation.emailError(""))
        assertEquals(R.string.error_email_empty, AuthValidation.emailError("   "))
    }

    @Test
    fun `surrounding whitespace does not invalidate an address`() {
        assertNull(AuthValidation.emailError("  alex@reps.app  "))
    }

    /**
     * Sign-in must not enforce the length rule: an existing account may predate
     * it, and rejecting a correct password locally would lock the user out of
     * their own account.
     */
    @Test
    fun `short password is allowed on sign in but not on sign up`() {
        assertNull(AuthValidation.passwordError("short", requireLength = false))
        assertEquals(
            R.string.error_password_short,
            AuthValidation.passwordError("short", requireLength = true),
        )
    }

    @Test
    fun `password of exactly the minimum length is accepted`() {
        assertNull(AuthValidation.passwordError("12345678", requireLength = true))
    }

    @Test
    fun `empty password is rejected in both modes`() {
        assertEquals(R.string.error_password_empty, AuthValidation.passwordError("", true))
        assertEquals(R.string.error_password_empty, AuthValidation.passwordError("", false))
    }

    /** Whitespace can be a legitimate password character, so it is not trimmed. */
    @Test
    fun `whitespace-only password counts toward length`() {
        assertNull(AuthValidation.passwordError("        ", requireLength = true))
    }

    @Test
    fun `name must not be blank`() {
        assertEquals(R.string.error_name_empty, AuthValidation.nameError(""))
        assertEquals(R.string.error_name_empty, AuthValidation.nameError("   "))
        assertNull(AuthValidation.nameError("Alex Rivera"))
    }
}
