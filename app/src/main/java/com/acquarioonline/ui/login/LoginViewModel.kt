package com.acquarioonline.ui.login

import android.util.Patterns
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.acquarioonline.R
import com.acquarioonline.data.auth.AuthRepository
import com.acquarioonline.data.auth.EmailAlreadyInUseException
import com.acquarioonline.data.auth.InvalidCredentialsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.acquarioonline.data.auth.UserNotKnownException

enum class AuthMode { SignIn, SignUp }

data class LoginUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    @StringRes val nameError: Int? = null,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    @StringRes val generalError: Int? = null
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) =
        _uiState.update { it.copy(name = value, nameError = null, generalError = null) }

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, generalError = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, generalError = null) }

    fun toggleMode() = _uiState.update {
        it.copy(
            mode = if (it.mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn,
            nameError = null,
            emailError = null,
            passwordError = null,
            generalError = null
        )
    }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading) return

        val email = state.email.trim()
        val nameError =
            if (state.mode == AuthMode.SignUp && state.name.isBlank()) R.string.error_name_required else null
        val emailError =
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) R.string.error_email_invalid else null
        val passwordError =
            if (state.password.length < MIN_PASSWORD_LENGTH) R.string.error_password_short else null

        if (nameError != null || emailError != null || passwordError != null) {
            _uiState.update {
                it.copy(nameError = nameError, emailError = emailError, passwordError = passwordError)
            }
            return
        }

        runAuth {
            if (state.mode == AuthMode.SignUp) {
                auth.signUp(state.name.trim(), email, state.password)
            } else {
                auth.signIn(email, state.password)
            }
        }
    }

    /**
     * Esegue l'operazione di autenticazione mostrando il caricamento.
     * In caso di successo non serve fare altro: [com.acquarioonline.ui.SessionViewModel]
     * vede il nuovo utente e l'app passa alla home.
     */
    private fun runAuth(block: suspend () -> Unit) {
        _uiState.update { it.copy(isLoading = true, generalError = null) }
        viewModelScope.launch {
            try {
                block()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, generalError = e.toErrorRes()) }
            }
        }
    }


    fun changePassword(email: String, newPassword:String){
        viewModelScope.launch {
            try {
                auth.changeHashedPassword(email,newPassword)
                _uiState.update {it.copy(generalError = null) }
            }catch (e: Exception){
                _uiState.update { it.copy(generalError = R.string.error_generic) }
            }
        }
    }



    private fun Throwable.toErrorRes(): Int = when (this) {
        is EmailAlreadyInUseException -> R.string.error_email_in_use
        is InvalidCredentialsException -> R.string.error_invalid_credentials
        is UserNotKnownException -> R.string.error_user_not_exists
        else -> R.string.error_generic
    }
    fun updatePassword(email:String, newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                auth.changeHashedPassword(email,newPassword)
                _uiState.update {it.copy(generalError = null) }
            }catch (e: Exception){
                _uiState.update { it.copy(generalError = R.string.error_generic) }
            }
        }
    }
    fun clearError() {
        _uiState.update { it.copy(generalError = null) }
    }
    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
    }


}
