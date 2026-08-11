package com.scream.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.scream.app.network.MeshForegroundService
import com.scream.app.ui.theme.ScreamTheme

class MainActivity : ComponentActivity() {

    // ──────────────────────────────────────────────────────────────────────────
    // Request only permissions needed to bring up nearby discovery. Feature-specific
    // permissions (microphone/camera) are requested when those features are used.
    // ──────────────────────────────────────────────────────────────────────────
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Log any denials; the mesh will still function with whatever is granted.
        results.entries.forEach { (perm, granted) ->
            if (!granted) android.util.Log.w("MainActivity", "Permission denied: $perm")
        }
        // Keep the app usable when discovery permissions are declined; the service can
        // retry later when the user enables them.
        startMeshService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
            // Location is required for BLE scanning on Android 11 and older.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                !granted(Manifest.permission.ACCESS_FINE_LOCATION)
            ) add(Manifest.permission.ACCESS_FINE_LOCATION)
            // Notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!granted(Manifest.permission.POST_NOTIFICATIONS))
                    add(Manifest.permission.POST_NOTIFICATIONS)
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
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start MeshForegroundService: ${e.message}")
        }
    }
}
