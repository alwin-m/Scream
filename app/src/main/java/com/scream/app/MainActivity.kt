package com.scream.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.scream.app.network.MeshForegroundService
import com.scream.app.ui.theme.ScreamTheme

class MainActivity : ComponentActivity() {

    // ──────────────────────────────────────────────────────────────────────────
    // Runtime permission launcher — requests all Bluetooth + notification perms
    // ──────────────────────────────────────────────────────────────────────────
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Log any denials; the mesh will still function with whatever is granted.
        results.entries.forEach { (perm, granted) ->
            if (!granted) android.util.Log.w("MainActivity", "Permission denied: $perm")
        }
        // Start the mesh service regardless — it handles missing permissions gracefully.
        startMeshService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ScreamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }

        requestRequiredPermissions()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Permission + service startup
    // ──────────────────────────────────────────────────────────────────────────

    private fun requestRequiredPermissions() {
        val needed = buildList {
            // Android 12+ Bluetooth permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!granted(Manifest.permission.BLUETOOTH_SCAN))
                    add(Manifest.permission.BLUETOOTH_SCAN)
                if (!granted(Manifest.permission.BLUETOOTH_CONNECT))
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                if (!granted(Manifest.permission.BLUETOOTH_ADVERTISE))
                    add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            // Fine location (required for BLE on Android < 12)
            if (!granted(Manifest.permission.ACCESS_FINE_LOCATION))
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            // Notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!granted(Manifest.permission.POST_NOTIFICATIONS))
                    add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // RECORD_AUDIO (Microphone for voice notes)
            if (!granted(Manifest.permission.RECORD_AUDIO))
                add(Manifest.permission.RECORD_AUDIO)
            // CAMERA (taking photos directly)
            if (!granted(Manifest.permission.CAMERA))
                add(Manifest.permission.CAMERA)
            // Storage/Media Access for photo/video sharing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!granted(Manifest.permission.READ_MEDIA_IMAGES))
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                if (!granted(Manifest.permission.READ_MEDIA_VIDEO))
                    add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                if (!granted(Manifest.permission.READ_EXTERNAL_STORAGE))
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (needed.isEmpty()) {
            // All already granted — start the service immediately
            startMeshService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startMeshService() {
        try {
            val intent = Intent(this, MeshForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            requestBatteryOptimizationExemption()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start MeshForegroundService: ${e.message}")
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Failed to request battery optimization exemption: ${e.message}")
                }
            }
        }
    }
}
