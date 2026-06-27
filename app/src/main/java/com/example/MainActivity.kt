package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.MainScreen
import com.example.ui.PermissionsScreen
import com.example.ui.isAccessibilityServiceEnabled
import com.example.ui.theme.MyApplicationTheme
import android.provider.Settings

class MainActivity : ComponentActivity() {
    private var permissionsGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showInterstitial by remember { mutableStateOf(true) }
                    
                    if (permissionsGranted) {
                        if (showInterstitial) {
                            com.example.ui.InterstitialAdPlaceholder(onDismiss = { showInterstitial = false })
                        }
                        MainScreen()
                    } else {
                        PermissionsScreen(onPermissionsGranted = {
                            permissionsGranted = true
                        })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        permissionsGranted = Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled(this)
    }
}
