package com.scream.app.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BluetoothBootReceiver — the wake-up trigger for the SCREAM mesh.
 *
 * Registered in the manifest for two system broadcasts:
 *
 *   1. [BluetoothAdapter.ACTION_STATE_CHANGED]
 *      Fires whenever the user toggles Bluetooth. When Bluetooth turns ON,
 *      we immediately start [MeshForegroundService] so peer discovery begins
 *      without the user ever opening the app.
 *
 *   2. [Intent.ACTION_BOOT_COMPLETED]
 *      Fires after the device boots. If Bluetooth is already enabled at boot
 *      (e.g., the phone rebooted while BT was on), we start the service right away.
 *
 * This receiver is declared in the manifest with android:exported="true" so
 * Android can deliver both protected broadcasts to it.
 */
class BluetoothBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BluetoothBootReceiver"
        // HTC / Xiaomi devices fire this instead of BOOT_COMPLETED in some ROMs
        private const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // ── Bluetooth state change ──────────────────────────────────────
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        Log.d(TAG, "Bluetooth turned ON — launching MeshForegroundService")
                        startMeshService(context)
                    }
                    BluetoothAdapter.STATE_TURNING_OFF,
                    BluetoothAdapter.STATE_OFF -> {
                        Log.d(TAG, "Bluetooth turning OFF — mesh will pause BLE operations")
                        // The running service detects this via its own BT receiver
                        // and stops BLE advertising/scanning gracefully.
                    }
                }
            }

            // ── Device boot ─────────────────────────────────────────────────
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_QUICKBOOT -> {
                Log.d(TAG, "Device booted — checking if Bluetooth is already ON")
                if (isBluetoothEnabled(context)) {
                    Log.d(TAG, "BT is ON at boot — launching MeshForegroundService")
                    startMeshService(context)
                } else {
                    Log.d(TAG, "BT is OFF at boot — service will start when BT is enabled")
                }
            }
        }
    }

    private fun startMeshService(context: Context) {
        try {
            val serviceIntent = Intent(context, MeshForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MeshForegroundService: ${e.message}")
        }
    }

    private fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bm?.adapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }
}
