package com.reps.app.feature.auth.signup

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

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val termsAccepted: Boolean = false,
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    val formError: String? = null,
    val loading: Boolean = false,
    val signedUp: Boolean = false,
) {
    /** The brief requires the terms checkbox to gate the button. */
    val canSubmit: Boolean get() = termsAccepted && !loading
}

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState = _uiState.asStateFlow()

    fun onNameChange(value: String) =
        _uiState.update { it.copy(name = value, nameError = null, formError = null) }

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, formError = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, formError = null) }

    fun onTermsChange(accepted: Boolean) =
        _uiState.update { it.copy(termsAccepted = accepted) }

    fun signUp() {
        val state = _uiState.value
        if (!state.canSubmit) return

        val nameError = AuthValidation.nameError(state.name)
        val emailError = AuthValidation.emailError(state.email)
        // New passwords must meet the minimum length; existing ones need not.
        val passwordError = AuthValidation.passwordError(state.password, requireLength = true)
        if (nameError != null || emailError != null || passwordError != null) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    emailError = emailError,
                    passwordError = passwordError,
                )
            }
            return
        }

        _uiState.update { it.copy(loading = true, formError = null) }
        viewModelScope.launch {
            val result = authRepository.signUp(
                name = state.name.trim(),
                email = state.email.trim(),
                password = state.password,
            )
            when (result) {
                is AuthResult.Success -> _uiState.update { it.copy(loading = false, signedUp = true) }
                is AuthResult.Failure ->
                    _uiState.update { it.copy(loading = false, formError = result.message) }
            }
        }
    }
}
