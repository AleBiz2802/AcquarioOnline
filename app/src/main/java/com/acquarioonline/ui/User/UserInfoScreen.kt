package com.acquarioonline.ui.User

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acquarioonline.R
import com.acquarioonline.ui.components.FormTextField
import com.acquarioonline.ui.login.BiometricPromptManager
import com.acquarioonline.ui.login.BiometricPromptManager.BiometricResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInfoScreen(
    biometricPromptManager: BiometricPromptManager,
    viewModel: UserInfoViewModel,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    var newValue by remember { mutableStateOf("") }
    var confirmValue by remember { mutableStateOf("") }
    var valueError by remember { mutableStateOf<Int?>(null) }
    var confirmError by remember { mutableStateOf<Int?>(null) }
    var isCredentialEditState by remember { mutableStateOf(false) }
    var elementEdited by remember {mutableStateOf<String?>(null) }
    var showIdentityChecker by remember { mutableStateOf(false) }
    var showCredentialChange by remember { mutableStateOf(false) }
    var password = stringResource(R.string.password)
    var email = stringResource(R.string.email)
    var name = stringResource(R.string.name)
    var confirmIdentity= stringResource(R.string.confirm_identity)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.userAccount)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )

            Spacer(Modifier.height(8.dp))

            UserInfoCard(
                icon = Icons.Filled.Person,
                label = stringResource(R.string.field_name),
                value = user?.displayName ?: "-",
                onEditClick ={
                    viewModel.clearError()
                    isCredentialEditState = true
                    elementEdited="name"
                }
            )

            UserInfoCard(
                icon = Icons.Filled.Email,
                label = stringResource(R.string.field_email),
                value = user?.email ?: "-",
                onEditClick = {
                    viewModel.clearError()
                    isCredentialEditState = true
                    elementEdited="email"
                }
            )

            UserInfoCard(
                icon = Icons.Filled.Lock,
                label = stringResource(R.string.field_password),
                value = "••••••••",
                onEditClick = {
                    viewModel.clearError()
                    isCredentialEditState = true
                    elementEdited="password"
                }
            )
        }
    }
    if(isCredentialEditState){
        AlertDialog(
            onDismissRequest = { isCredentialEditState= false },
            title = { Text(if(elementEdited=="email")
                                stringResource(R.string.new_email)
                            else if(elementEdited=="password")
                                stringResource(R.string.new_password)
                            else
                                stringResource(R.string.new_name)) },
            text = {

                    Text(if(elementEdited=="email")
                        stringResource(R.string.new_email_confirm)
                    else if(elementEdited=="password")
                        stringResource(R.string.new_password_confirm)
                    else
                        stringResource(R.string.new_name_confirm))

            },
            confirmButton = {
                TextButton(
                    onClick = {
                       isCredentialEditState=false
                        showIdentityChecker = true
                    }
                ) {
                    Text(stringResource(R.string.edit))
                }
            },
            dismissButton = {
                TextButton(onClick = { isCredentialEditState = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    LaunchedEffect(showIdentityChecker) {
        if(showIdentityChecker){
            biometricPromptManager.showBiometricPrompt(
                title = if(elementEdited=="email")
                            "Email"
                        else if(elementEdited=="password")
                            "Password"
                        else
                            "Name",
                description = "Confirm your identity"
            )
        }
    }
    if(
        showIdentityChecker
    ){
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val biometricResult by biometricPromptManager._promptResult.collectAsState(initial = null)
            val enrollLauncher = rememberLauncherForActivityResult(contract= ActivityResultContracts.StartActivityForResult(),onResult={
            })
            LaunchedEffect(biometricResult){
                if(biometricResult is BiometricResult.AuthSuccess){
                    showIdentityChecker=false
                    showCredentialChange=true
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
                            title = if(elementEdited=="email")
                             //stringResource(R.string.new_email)
                                email
                            else if(elementEdited=="password")
                            //stringResource(R.string.new_password)
                                password
                            else
                            /// stringResource(R.string.new_name),
                                name,
                            description = confirmIdentity
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


    if (showCredentialChange) {
        CredentialChangerSheet(
            toChange = elementEdited ?: "",
            value = newValue,
            confirmValue = confirmValue,
            onValueChange = {
                newValue = it
                valueError = null
                viewModel.clearError()
            },
            onConfirmValueChange = {
                confirmValue = it
                confirmError = null
                viewModel.clearError()
            },
            valueError = valueError ?: uiState.error,
            confirmError = confirmError,
            onDismiss = {
                showCredentialChange = false
                newValue = ""
                confirmValue = ""
                valueError = null
                confirmError = null
            },
            onConfirm = {
                valueError = null
                confirmError = null

                when (elementEdited) {
                    "email" -> {
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newValue).matches()) {
                            valueError = R.string.error_email_invalid
                            return@CredentialChangerSheet
                        }
                        viewModel.updateEmail(newValue) {
                            showCredentialChange = false
                            newValue = ""
                            confirmValue = ""
                        }
                    }
                    "password" -> {
                        if (newValue.length < 6) {
                            valueError = R.string.error_password_short
                            return@CredentialChangerSheet
                        }
                        if (newValue != confirmValue) {
                            confirmError = R.string.error_passwords_mismatch
                            return@CredentialChangerSheet
                        }
                        viewModel.updatePassword(newValue) {
                            showCredentialChange = false
                            newValue = ""
                            confirmValue = ""
                        }
                    }
                    "name" -> {
                        if (newValue.isBlank()) {
                            valueError = R.string.error_name_required
                            return@CredentialChangerSheet
                        }
                        viewModel.updateName(newValue) {
                            showCredentialChange = false
                            newValue = ""
                            confirmValue = ""
                        }
                    }
                }

            }
        )
    }

}


@Composable
private fun UserInfoCard(
    icon: ImageVector,
    label: String,
    value: String,
    onEditClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSystemInDarkTheme()) Color(0xFF003A48) else Color(0xFFE0F2FE),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (onEditClick != null) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.edit)+": $label",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}