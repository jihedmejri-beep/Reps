package com.reps.app.feature.auth

import androidx.annotation.StringRes
import com.reps.app.R
import com.reps.app.core.constants.AppConstants

/**
 * Field validation shared by login and sign up.
 *
 * Errors are returned as string resource ids rather than formatted text so the
 * messages localise with the rest of the app.
 */
object AuthValidation {

    /**
     * Deliberately permissive: something before an @, something after it, and a
     * dot in the domain. Anything stricter starts rejecting addresses that are
     * actually valid, and the server is the real authority anyway.
     */
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    @StringRes
    fun emailError(email: String): Int? = when {
        email.isBlank() -> R.string.error_email_empty
        !EMAIL_REGEX.matches(email.trim()) -> R.string.error_email_invalid
        else -> null
    }

    @StringRes
    fun passwordError(password: String, requireLength: Boolean): Int? = when {
        password.isEmpty() -> R.string.error_password_empty
        requireLength && password.length < AppConstants.MIN_PASSWORD_LENGTH ->
            R.string.error_password_short
        else -> null
    }

    @StringRes
    fun nameError(name: String): Int? =
        if (name.isBlank()) R.string.error_name_empty else null
}
