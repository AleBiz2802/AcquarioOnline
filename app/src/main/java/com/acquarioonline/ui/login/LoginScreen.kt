package com.acquarioonline.ui.login

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acquarioonline.R
import com.acquarioonline.ui.AppViewModelProvider
import com.acquarioonline.ui.components.FormTextField
import com.acquarioonline.ui.components.PasswordVisibilityToggle
import com.acquarioonline.ui.login.BiometricPromptManager.BiometricResult
import kotlin.contracts.contract

@Composable
fun LoginScreen(
    biometricPromptManager: BiometricPromptManager,
    viewModel: LoginViewModel = viewModel(factory = AppViewModelProvider.Factory)

) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showChangePasswordDialog by remember {mutableStateOf(false)}
    var enableChangePassword by remember{mutableStateOf(false)}
    var newPassword by remember { mutableStateOf("") }
    val isSignUp = state.mode == AuthMode.SignUp
    val confirmTitle = stringResource(R.string.confirm_identity)
    val confirmMessage = stringResource(R.string.confirm_identity_message)
    val changePassword = stringResource(R.string.change_password)
    val confirmIdentity = stringResource(R.string.confirm_identity_message)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_drop),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        if (isSignUp) {
            FormTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = stringResource(R.string.field_name),
                error = state.nameError,
                enabled = !state.isLoading
            )
        }
        FormTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = stringResource(R.string.field_email),
            error = state.emailError,
            enabled = !state.isLoading,
            keyboardType = KeyboardType.Email
        )
        FormTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.field_password),
            error = state.passwordError,
            enabled = !state.isLoading,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeDone = viewModel::submit,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                PasswordVisibilityToggle(visible = showPassword, onToggle = { showPassword = !showPassword })
            }
        )

        state.generalError?.let {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = viewModel::submit,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(if (isSignUp) R.string.sign_up else R.string.sign_in))
            }
        }

        TextButton(onClick = viewModel::toggleMode, enabled = !state.isLoading) {
            Text(stringResource(if (isSignUp) R.string.switch_to_sign_in else R.string.switch_to_sign_up))
        }

        if (state.generalError == R.string.error_invalid_credentials) {
            TextButton(
                onClick = { showChangePasswordDialog = true },
                enabled = !state.isLoading,
            ) {
                Text(
                    text = changePassword,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showChangePasswordDialog) {

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text(changePassword) },
            text = {
                Column {
                    Text(stringResource(R.string.insert_new_password)+"${state.email}")
                    Spacer(Modifier.height(8.dp))
                    FormTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = stringResource(R.string.new_password),
                        keyboardType = KeyboardType.Password
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPassword.length >= 6) {
                            //viewModel.changePassword(state.email, newPassword)
                            showChangePasswordDialog = false
                           enableChangePassword = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LaunchedEffect(enableChangePassword) {
        if(enableChangePassword){
            biometricPromptManager.showBiometricPrompt(
                title = changePassword,
                description = confirmIdentity
            )
        }
    }
    if(enableChangePassword){
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val biometricResult by biometricPromptManager._promptResult.collectAsState(initial = null)
            val enrollLauncher = rememberLauncherForActivityResult(contract= ActivityResultContracts.StartActivityForResult(),onResult={
            })
            LaunchedEffect(biometricResult){
                if(biometricResult is BiometricResult.AuthSuccess){
                    viewModel.changePassword(state.email, newPassword)
                    enableChangePassword=false
                }
                if(biometricResult is BiometricResult.AuthNotSet ){
                    if(Build.VERSION.SDK_INT>=30){
                        val enrollIntent= Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                            putExtra(
                                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            )
                        }
                        enrollLauncher.launch(enrollIntent)
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment =  Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        biometricPromptManager.showBiometricPrompt(
                            title = confirmTitle,
                            description= confirmMessage
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.authentication))
                }
               biometricResult?.let{
                       result-> Text(
                       text = when(result){
                           is BiometricResult.AuthError -> {
                               result.error
                           }
                           BiometricResult.AuthFailed ->{
                               "Auth failed"
                           }
                           BiometricResult.AuthNotSet ->{
                               "Auth not set"
                           }
                           BiometricResult.AuthSuccess ->{
                               "Auth success"
                           }
                           BiometricResult.FeatureNotAvailable ->{
                               "Feature not available"
                           }
                           BiometricResult.HardwareNotAvailable ->{
                               "Hardware not available"
                           }
                       }
                   )
               }
            }
        }
    }



}
