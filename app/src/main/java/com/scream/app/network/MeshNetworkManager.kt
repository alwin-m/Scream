package com.scream.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.scream.app.data.ScreamRepository
import com.scream.app.model.NetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * MeshNetworkManager — BLE advertising + scanning layer.
 *
 * Responsibilities:
 *   • Advertise the SCREAM Mesh Service UUID so nearby devices can find us
 *   • Scan for nearby BLE peripherals advertising the same UUID
 *   • When a SCREAM peer is discovered, hand it to [BleGattClient] for connection
 *   • React to Bluetooth state changes (BT on/off) to restart/stop BLE operations
 *
 * Only SCREAM devices (matching our service UUID) are passed to [BleGattClient].
 * Non-SCREAM BLE devices are ignored.
 */
object MeshNetworkManager {
    private const val TAG = "MeshNetworkManager"

    @Volatile
    private var running = false

    private var context: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var isBleScanning = false
    private var isBleAdvertising = false

    // All BLE devices seen recently — not just SCREAM ones
    private val discoveredDevices = ConcurrentHashMap<String, DiscoveredDevice>()

    data class DiscoveredDevice(
        val address: String,
        val name: String,
        val rssi: Int,
        val timestamp: Long,
        val isScreamDevice: Boolean,
        val isBitChatDevice: Boolean = false
    )

    // ──────────────────────────────────────────────────────────────────────────
    // BLE scan callback — fires on every discovered device
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device: BluetoothDevice = result.device ?: return
            val address: String = device.address ?: return
            val name: String = device.name ?: "Unknown"
            val rssi: Int = result.rssi

            // Check whether this device advertises SCREAM or BitChat service UUID
            val serviceUuids = result.scanRecord?.serviceUuids
            val isScream = serviceUuids?.any {
                it.uuid == BleGattServer.SCREAM_SERVICE_UUID
            } == true
            val isBitChat = serviceUuids?.any {
                it.uuid == BitChatProtocolAdapter.BITCHAT_SERVICE_UUID
            } == true || name.contains("BitChat", ignoreCase = true)

            discoveredDevices[address] = DiscoveredDevice(
                address = address,
                name = name,
                rssi = rssi,
                timestamp = System.currentTimeMillis(),
                isScreamDevice = isScream,
                isBitChatDevice = isBitChat
            )

            if (isScream || isBitChat) {
                val protoTag = if (isBitChat) "BitChat" else "SCREAM"
                Log.d(TAG, "$protoTag peer discovered via BLE scan: $name ($address) RSSI=$rssi")
                P2pMeshEngine.registerBlePeerDiscovered(address, name, rssi, isBitChat = isBitChat)
                context?.let { ctx ->
                    BleGattClient.connectToDevice(ctx, device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            isBleScanning = false
            // Retry scan after a delay
            scope.launch {
                delay(10_000)
                if (running) startBleScanning()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BLE advertise callback
    // ──────────────────────────────────────────────────────────────────────────

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isBleAdvertising = true
            Log.d(TAG, "BLE advertising started — broadcasting SCREAM service UUID")
        }

        override fun onStartFailure(errorCode: Int) {
            isBleAdvertising = false
            Log.e(TAG, "BLE advertising failed with code: $errorCode")
            // Retry after a delay
            scope.launch {
                delay(15_000)
                if (running) startBleAdvertising()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BT state receiver — restarts operations when BT is toggled
    // ──────────────────────────────────────────────────────────────────────────

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                ?: return
            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    Log.d(TAG, "Bluetooth ON — (re)starting BLE advertising and scanning")
                    startBleAdvertising()
                    startBleScanning()
                    ScreamRepository.updateNetworkStatus(NetworkStatus.ACTIVE)
                }
                BluetoothAdapter.STATE_TURNING_OFF -> {
                    // Stop immediately; STATE_OFF may come too late
                    stopBleAdvertising()
                    stopBleScanning()
                    ScreamRepository.updateNetworkStatus(NetworkStatus.OFFLINE)
                }
            }
        }
    }

    private var bluetoothReceiverRegistered = false

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    fun start(context: Context) {
        if (running) return
        this.context = context.applicationContext
        running = true

        registerBluetoothReceiver()
        startBleAdvertising()
        startBleScanning()
        startPeriodicCleanup()

        Log.d(TAG, "MeshNetworkManager started")
    }

    fun stop() {
        running = false
        stopBleAdvertising()
        stopBleScanning()
        unregisterBluetoothReceiver()
        BleGattClient.disconnectAll()
        discoveredDevices.clear()
        Log.d(TAG, "MeshNetworkManager stopped")
    }

    fun isRunning(): Boolean = running

    fun getDiscoveredDeviceCount(): Int = discoveredDevices.size
    fun getDiscoveredDevices(): List<DiscoveredDevice> = discoveredDevices.values.toList()
    fun getScreamPeerCount(): Int = discoveredDevices.values.count { it.isScreamDevice }

    // ──────────────────────────────────────────────────────────────────────────
    // BLE Advertising
    // ──────────────────────────────────────────────────────────────────────────

    private var isFastMode = false

    fun setFastDiscoveryMode(enabled: Boolean) {
        if (isFastMode == enabled) return
        isFastMode = enabled
        Log.d(TAG, "Fast discovery mode changed: $enabled")
        if (running) {
            stopBleAdvertising()
            stopBleScanning()
            startBleAdvertising()
            startBleScanning()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleAdvertising() {
        val ctx = context ?: return
        if (isBleAdvertising) return

        try {
            val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
            val adapter = bluetoothManager.adapter ?: return
            if (!adapter.isEnabled) return

            if (!adapter.isMultipleAdvertisementSupported) {
                Log.w(TAG, "BLE advertising not supported on this device")
                return
            }

            bleAdvertiser = adapter.bluetoothLeAdvertiser ?: run {
                Log.w(TAG, "BluetoothLeAdvertiser is null — cannot advertise")
                return
            }

            val mode = if (isFastMode) AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY else AdvertiseSettings.ADVERTISE_MODE_BALANCED
            val power = if (isFastMode) AdvertiseSettings.ADVERTISE_TX_POWER_HIGH else AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(mode)
                .setTxPowerLevel(power)
                .setConnectable(true)
                .setTimeout(0)
                .build()

            val meshId = ScreamRepository.getMeshId()
            val payloadBytes = meshId.takeLast(4).toByteArray(Charsets.UTF_8)

            val data = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(BleGattServer.SCREAM_SERVICE_UUID))
                .addServiceUuid(ParcelUuid(BitChatProtocolAdapter.BITCHAT_SERVICE_UUID))
                .addServiceData(ParcelUuid(BleGattServer.SCREAM_SERVICE_UUID), payloadBytes)
                .setIncludeDeviceName(false)
                .build()

            bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_ADVERTISE permission: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE advertising: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleAdvertising() {
        try {
            if (isBleAdvertising) {
                bleAdvertiser?.stopAdvertising(advertiseCallback)
                isBleAdvertising = false
                Log.d(TAG, "BLE advertising stopped")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping BLE advertising: ${e.message}")
        }
        bleAdvertiser = null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BLE Scanning
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startBleScanning() {
        val ctx = context ?: return
        if (isBleScanning) return

        try {
            val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
            val adapter = bluetoothManager.adapter ?: return
            if (!adapter.isEnabled) return

            bleScanner = adapter.bluetoothLeScanner ?: run {
                Log.w(TAG, "BluetoothLeScanner is null")
                return
            }

            val filters = listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(BleGattServer.SCREAM_SERVICE_UUID))
                    .build(),
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(BitChatProtocolAdapter.BITCHAT_SERVICE_UUID))
                    .build()
            )

            val scanMode = if (isFastMode) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED

            val settings = ScanSettings.Builder()
                .setScanMode(scanMode)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .build()

            bleScanner?.startScan(filters, settings, scanCallback)
            isBleScanning = true
            Log.d(TAG, "BLE scanning started (mode=${if (isFastMode) "LOW_LATENCY" else "BALANCED"})")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScanning() {
        try {
            if (isBleScanning) {
                bleScanner?.stopScan(scanCallback)
                isBleScanning = false
                Log.d(TAG, "BLE scanning stopped")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping BLE scan: ${e.message}")
        }
        bleScanner = null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Housekeeping
    // ──────────────────────────────────────────────────────────────────────────

    private fun startPeriodicCleanup() {
        scope.launch {
            while (running) {
                delay(30_000)
                val now = System.currentTimeMillis()
                val stale = discoveredDevices.entries.filter { now - it.value.timestamp > 60_000 }
                stale.forEach { discoveredDevices.remove(it.key) }
                if (stale.isNotEmpty()) {
                    Log.d(TAG, "Cleaned ${stale.size} stale BLE device entries")
                }
            }
        }
    }

    private fun registerBluetoothReceiver() {
        val ctx = context ?: return
        if (bluetoothReceiverRegistered) return
        try {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ctx.registerReceiver(bluetoothStateReceiver, filter)
            }
            bluetoothReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Error registering BT receiver: ${e.message}")
        }
    }

    private fun unregisterBluetoothReceiver() {
        if (!bluetoothReceiverRegistered) return
        try {
            context?.unregisterReceiver(bluetoothStateReceiver)
            bluetoothReceiverRegistered = false
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering BT receiver: ${e.message}")
        }
    }
}
