package com.acquarioonline.ui.login

import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class BiometricPromptManager(
    private val activity: AppCompatActivity
){
    private val biometricsResultChannel = Channel<BiometricResult>()
    val _promptResult = biometricsResultChannel.receiveAsFlow()
    fun showBiometricPrompt(
        title: String,
        description: String
    ){
        val biometricManager= BiometricManager.from(activity)
        val authenticators = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_STRONG
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setDescription(description)

        if (Build.VERSION.SDK_INT < 30) {
            promptInfo.setNegativeButtonText("Annulla")
            promptInfo.setAllowedAuthenticators(BIOMETRIC_STRONG)
        } else {
            promptInfo.setAllowedAuthenticators(authenticators)
        }
        when(biometricManager.canAuthenticate(authenticators)){
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE->{
                biometricsResultChannel.trySend(BiometricResult.HardwareNotAvailable)
                return
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE->{
                biometricsResultChannel.trySend(BiometricResult.FeatureNotAvailable)
                return
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED->{
                biometricsResultChannel.trySend(BiometricResult.AuthNotSet)
                return
            }
            else-> Unit
        }

        val prompt = BiometricPrompt (
            activity,
            object: BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    biometricsResultChannel.trySend(BiometricResult.AuthError(errString.toString()))
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    biometricsResultChannel.trySend(BiometricResult.AuthSuccess)

                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    biometricsResultChannel.trySend(BiometricResult.AuthFailed)

                }
            }
        )
        prompt.authenticate(promptInfo.build())
    }

    sealed interface  BiometricResult{
        data object HardwareNotAvailable: BiometricResult
        data object FeatureNotAvailable: BiometricResult
        data class AuthError(val error: String): BiometricResult
        data object AuthFailed: BiometricResult
        data object AuthSuccess: BiometricResult
        data object AuthNotSet: BiometricResult
    }
}

