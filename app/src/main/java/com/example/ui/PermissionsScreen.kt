package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.service.AutoClickerService

@Composable
fun PermissionsScreen(onPermissionsGranted: () -> Unit) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var showDisclosure by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
                if (hasOverlayPermission && hasAccessibilityPermission) {
                    onPermissionsGranted()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showDisclosure) {
        AccessibilityDisclosure(
            onAccept = {
                showDisclosure = false
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            onDecline = { showDisclosure = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Permissions Required", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Display Over Other Apps", fontWeight = FontWeight.Bold)
                Text("Needed to show the floating control panel and script builder over your games or apps.")
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    enabled = !hasOverlayPermission
                ) {
                    Text(if (hasOverlayPermission) "Granted" else "Grant Overlay Permission")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Accessibility Service", fontWeight = FontWeight.Bold)
                Text("Needed to simulate taps and swipes on your screen.")
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showDisclosure = true },
                    enabled = !hasAccessibilityPermission
                ) {
                    Text(if (hasAccessibilityPermission) "Granted" else "Grant Accessibility Permission")
                }
            }
        }
    }
}

@Composable
fun AccessibilityDisclosure(onAccept: () -> Unit, onDecline: () -> Unit) {
    var isChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Important Disclosure", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Auto Clicker uses the AccessibilityService API to perform simulated taps and swipes on your screen on your behalf. " +
                    "This is the core functionality of the app, allowing it to automate repetitive tasks based on the scripts you create.\n\n" +
                    "We do NOT use the Accessibility API to read your screen content, monitor your inputs, or collect any personal or sensitive data. " +
                    "Every action performed by this app originates exclusively from a script you explicitly recorded or configured.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isChecked, onCheckedChange = { isChecked = it })
            Text("I understand and agree")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onDecline) {
                Text("Decline")
            }
            Button(onClick = onAccept, enabled = isChecked) {
                Text("Continue to Settings")
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    return AutoClickerService.instance != null
}
