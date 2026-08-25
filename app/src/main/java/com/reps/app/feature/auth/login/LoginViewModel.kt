package com.reps.app.feature.auth.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.domain.repository.AuthRepository
import com.reps.app.domain.repository.AuthResult
import com.reps.app.feature.auth.AuthValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    val formError: String? = null,
    val loading: Boolean = false,
    val signedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        // Clearing the error as the user types avoids scolding them mid-fix.
        _uiState.update { it.copy(email = value, emailError = null, formError = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, formError = null) }
    }

    fun signIn() {
        val state = _uiState.value
        if (state.loading) return

        val emailError = AuthValidation.emailError(state.email)
        // Length is not enforced on sign-in: an existing account may predate the
        // current rule, and the server decides anyway.
        val passwordError = AuthValidation.passwordError(state.password, requireLength = false)
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _uiState.update { it.copy(loading = true, formError = null) }
        viewModelScope.launch {
            when (val result = authRepository.signIn(state.email.trim(), state.password)) {
                is AuthResult.Success -> _uiState.update { it.copy(loading = false, signedIn = true) }
                is AuthResult.Failure ->
                    _uiState.update { it.copy(loading = false, formError = result.message) }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        if (_uiState.value.loading) return
        _uiState.update { it.copy(loading = true, formError = null) }
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(idToken)) {
                is AuthResult.Success -> _uiState.update { it.copy(loading = false, signedIn = true) }
                is AuthResult.Failure ->
                    _uiState.update { it.copy(loading = false, formError = result.message) }
            }
        }
    }

    /** Reuses the email field, so it must be valid before we can send anything. */
    fun sendPasswordReset() {
        val email = _uiState.value.email
        val emailError = AuthValidation.emailError(email)
        if (emailError != null) {
            _uiState.update { it.copy(emailError = emailError) }
            return
        }
        viewModelScope.launch {
            authRepository.sendPasswordReset(email.trim())
        }
    }
}
