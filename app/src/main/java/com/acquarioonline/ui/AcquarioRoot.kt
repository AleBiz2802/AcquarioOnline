package com.acquarioonline.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acquarioonline.ui.home.HomeScreen
import com.acquarioonline.ui.login.*
/** Radice dell'interfaccia: login se non c'è sessione, altrimenti la home. */
@Composable
fun AcquarioRoot(
    biometricPromptManager: BiometricPromptManager,
    sessionViewModel: SessionViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val user by sessionViewModel.user.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (user == null) LoginScreen( biometricPromptManager) else HomeScreen()
    }
}
