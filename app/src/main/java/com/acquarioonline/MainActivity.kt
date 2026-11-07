package com.acquarioonline

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.acquarioonline.data.service.AquariumMonitoringService
import com.acquarioonline.ui.AcquarioRoot
import com.acquarioonline.ui.login.BiometricPromptManager
import com.acquarioonline.ui.theme.AcquarioTheme

class MainActivity : AppCompatActivity() {
    private val promptManager: BiometricPromptManager = BiometricPromptManager(this)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val serviceIntent = Intent(this, AquariumMonitoringService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        setContent {
            AcquarioTheme {
                AcquarioRoot(promptManager)
            }
        }
    }
}
