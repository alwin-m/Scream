package com.scream.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import com.scream.app.model.PeerTransport

object TransportManager {
    private const val TAG = "TransportManager"

    private var context: Context? = null
    private var cachedBestTransport: PeerTransport? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    @SuppressLint("MissingPermission")
    fun getBestAvailableTransport(): PeerTransport {
        val ctx = context ?: return PeerTransport.UNKNOWN

        val transports = mutableListOf<PeerTransport>()

        try {
            val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter
            if (adapter != null && adapter.isEnabled) {
                transports.add(PeerTransport.BLUETOOTH)

                if (adapter.isMultipleAdvertisementSupported) {
                    transports.add(PeerTransport.BLE)
                }
            }
        } catch (e: SecurityException) {
            Log.d(TAG, "Missing Bluetooth permission")
        }

        try {
            val wifiManager = ctx.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            if (wifiManager != null) {
                transports.add(PeerTransport.WIFI_DIRECT)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Wi-Fi Direct not available: ${e.message}")
        }

        transports.add(PeerTransport.NEARBY)
        transports.add(PeerTransport.TCP)

        val best = transports.firstOrNull() ?: PeerTransport.UNKNOWN
        cachedBestTransport = best
        Log.d(TAG, "Best available transport: ${best.displayName} (from ${transports.size} options)")
        return best
    }

    fun getCachedBestTransport(): PeerTransport {
        return cachedBestTransport ?: getBestAvailableTransport()
    }

    fun isBluetoothAvailable(): Boolean {
        return try {
            val ctx = context ?: return false
            val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
    }

    fun isWifiDirectAvailable(): Boolean {
        return try {
            val ctx = context ?: return false
            val wifiManager = ctx.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            wifiManager != null
        } catch (e: Exception) {
            false
        }
    }

    fun getTransportPriority(): List<PeerTransport> = listOf(
        PeerTransport.WIFI_DIRECT,
        PeerTransport.BLUETOOTH,
        PeerTransport.BLE,
        PeerTransport.NEARBY,
        PeerTransport.TCP,
        PeerTransport.UNKNOWN
    )
}
