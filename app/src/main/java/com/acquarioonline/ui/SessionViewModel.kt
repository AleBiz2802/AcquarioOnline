package com.acquarioonline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.acquarioonline.data.auth.AuthRepository
import com.acquarioonline.data.auth.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Espone la sessione corrente: null = login, altrimenti home. */
class SessionViewModel(auth: AuthRepository) : ViewModel() {
    val user: StateFlow<UserProfile?> = auth.currentUser.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = auth.currentUserSnapshot()
    )
}
