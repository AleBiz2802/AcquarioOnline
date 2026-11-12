package com.acquarioonline.ui.User
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.acquarioonline.R
import com.acquarioonline.data.auth.AuthRepository
import com.acquarioonline.data.auth.EmailAlreadyInUseException
import com.acquarioonline.data.auth.UserProfile
import com.acquarioonline.ui.login.BiometricPromptManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
data class UserInfoUiState(
    @androidx.annotation.StringRes val error : Int? = null
)
class UserInfoViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    // Legge il profilo dell'utente attualmente loggato (Nome ed Email)
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(UserInfoUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<UserInfoUiState> = _uiState.asStateFlow()

    val currentUser: StateFlow<UserProfile?> = auth.currentUser.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = auth.currentUserSnapshot()
    )
    fun updatePassword(newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val current = currentUser.value
            if (current?.email != null) {
                try {
                    auth.changeHashedPassword(current.email, newPassword)
                    _uiState.update { it.copy(error = null) }
                    onSuccess()
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = R.string.error_generic) }
                }
            }
        }
    }
    fun updateName(newName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val current = currentUser.value
            if (current?.email != null) {
                try {
                    auth.changeUserName(current.email, newName)
                    _uiState.update { it.copy(error= null) }
                    onSuccess()
                } catch (e: Exception) {
                    _uiState.update { it.copy(error= R.string.error_generic) }
                }
            }
        }
    }
    fun updateEmail(newEmail: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val current = currentUser.value
            if (current?.email != null) {
                try {
                    auth.changeUserEmail(current.email, newEmail)
                    _uiState.update { it.copy(error= null) }
                    onSuccess()
                } catch (e: EmailAlreadyInUseException) {
                    _uiState.update { it.copy(error = R.string.error_email_in_use) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = R.string.error_generic) }
                }
            }
        }
    }


    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun factory(auth: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UserInfoViewModel(auth)
            }
        }
    }
}