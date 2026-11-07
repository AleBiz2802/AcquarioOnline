package com.acquarioonline.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.acquarioonline.AcquarioApp
import com.acquarioonline.ui.home.HomeViewModel
import com.acquarioonline.ui.login.LoginViewModel

/** Factory dei ViewModel che non hanno indici di navigazione. */
object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SessionViewModel(acquarioApp().container.authRepository) }
        initializer { LoginViewModel(acquarioApp().container.authRepository) }
        initializer {
            val container = acquarioApp().container
            HomeViewModel(container.authRepository, container.aquariumRepository, container.mqttManager, container.alarmManager)
        }
    }
}

private fun CreationExtras.acquarioApp(): AcquarioApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AcquarioApp
