package com.scream.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * BLE GATT Server for the SCREAM mesh.
 *
 * Hosts a fixed service with two characteristics:
 *   - WRITE_CHAR: remote SCREAM devices write JSON message payloads here (with chunking support)
 *   - NOTIFY_CHAR: this device pushes outgoing messages to all connected clients
 *
 * The UUIDs are fixed across all versions of the app to guarantee backward compatibility.
 */
object BleGattServer {
    private const val TAG = "BleGattServer"

    // Fixed UUIDs — MUST NOT change between app versions
    val SCREAM_SERVICE_UUID: UUID = UUID.fromString("0000FEA5-0000-1000-8000-00805F9B34FB")
    val WRITE_CHAR_UUID: UUID    = UUID.fromString("0000FEA6-0000-1000-8000-00805F9B34FB")
    val NOTIFY_CHAR_UUID: UUID   = UUID.fromString("0000FEA7-0000-1000-8000-00805F9B34FB")
    private val CCCD_UUID: UUID  = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // Max safe BLE payload without MTU negotiation (20-byte ATT header overhead)
    private const val NOTIFY_CHUNK_RAW = 120 // raw bytes before base64 encoding

    private var gattServer: BluetoothGattServer? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Per-sender chunk reassembly: shortId -> (chunkIndex -> base64EncodedChunk)
    private val chunkBuffer = mutableMapOf<String, MutableMap<Int, String>>()
    private val chunkTotals = mutableMapOf<String, Int>()

    // ──────────────────────────────────────────────────────────────────────────
    // GATT server callbacks
    // ──────────────────────────────────────────────────────────────────────────

    private val serverCallback = object : BluetoothGattServerCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            device ?: return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices.add(device)
                    Log.d(TAG, "GATT client connected: ${device.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(device)
                    Log.d(TAG, "GATT client disconnected: ${device.address}")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            // Always ack immediately so the client doesn't time out
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            val bytes = value ?: return
            if (bytes.isEmpty()) return
            processIncoming(String(bytes, Charsets.UTF_8), device?.address)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            // Client subscribed to notifications — great, it's ready
            Log.d(TAG, "Client subscribed to notifications: ${device?.address}")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (gattServer != null) return // already running

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val adapter = bluetoothManager.adapter ?: return
        if (!adapter.isEnabled) return

        try {
            gattServer = bluetoothManager.openGattServer(context, serverCallback)

            val service = BluetoothGattService(SCREAM_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            // Write characteristic — remote devices write messages here
            val writeChar = BluetoothGattCharacteristic(
                WRITE_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            // Notify characteristic — we push messages to subscribed clients
            val notifyChar = BluetoothGattCharacteristic(
                NOTIFY_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            val cccd = BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            notifyChar.addDescriptor(cccd)

            service.addCharacteristic(writeChar)
            service.addCharacteristic(notifyChar)
            gattServer?.addService(service)

            notifyCharacteristic = notifyChar
            Log.d(TAG, "BLE GATT Server started with service ${SCREAM_SERVICE_UUID}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to start GATT server: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GATT server: ${e.message}")
        }
    }

    /**
     * Push a JSON message string to all connected BLE centrals.
     * Automatically chunks messages that exceed the safe BLE MTU.
     */
    @SuppressLint("MissingPermission")
    fun notifyConnectedDevices(jsonString: String) {
        val char = notifyCharacteristic ?: return
        val server = gattServer ?: return
        if (connectedDevices.isEmpty()) return

        scope.launch {
            val bytes = jsonString.toByteArray(Charsets.UTF_8)
            if (bytes.size <= 180) {
                // Small enough for a single packet
                deliverNotification(server, char, bytes)
            } else {
                // Chunk it
                val shortId = UUID.randomUUID().toString().take(8)
                val rawChunks = bytes.toList().chunked(NOTIFY_CHUNK_RAW)
                val total = rawChunks.size

                rawChunks.forEachIndexed { index, chunk ->
                    val encoded = Base64.encodeToString(chunk.toByteArray(), Base64.NO_WRAP)
                    val packet = "SCHK:$shortId:$index/$total:$encoded".toByteArray(Charsets.UTF_8)
                    deliverNotification(server, char, packet)
                    kotlinx.coroutines.delay(20) // pacing between chunks
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun deliverNotification(
        server: BluetoothGattServer,
        char: BluetoothGattCharacteristic,
        bytes: ByteArray
    ) {
        connectedDevices.toList().forEach { device ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    server.notifyCharacteristicChanged(device, char, false, bytes)
                } else {
                    @Suppress("DEPRECATION")
                    char.value = bytes
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(device, char, false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Notify failed for ${device.address}: ${e.message}")
            }
        }
    }

    fun stop() {
        try {
            gattServer?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GATT server: ${e.message}")
        }
        gattServer = null
        notifyCharacteristic = null
        connectedDevices.clear()
        chunkBuffer.clear()
        chunkTotals.clear()
        Log.d(TAG, "BLE GATT Server stopped")
    }

    fun getConnectedDeviceCount(): Int = connectedDevices.size

    @SuppressLint("MissingPermission")
    fun disconnectDevice(address: String) {
        val device = connectedDevices.firstOrNull { it.address == address } ?: return
        connectedDevices.remove(device)
        runCatching { gattServer?.cancelConnection(device) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal — message reassembly and dispatch
    // ──────────────────────────────────────────────────────────────────────────

    private fun processIncoming(text: String, endpointAddress: String?) {
        if (text.startsWith("SCHK:")) {
            reassembleChunk(text, endpointAddress)
        } else {
            dispatchToEngine(text, endpointAddress)
        }
    }

    /**
     * Reassembles chunked BLE packets.
     * Format: SCHK:<shortId>:<index>/<total>:<base64Data>
     */
    private fun reassembleChunk(text: String, endpointAddress: String?) {
        try {
            // Split on first 3 colons only (data may contain colons in base64)
            val parts = text.split(":", limit = 4)
            if (parts.size < 4) return
            val shortId = parts[1]
            val indexTotalParts = parts[2].split("/")
            if (indexTotalParts.size < 2) return
            val index = indexTotalParts[0].toInt()
            val total = indexTotalParts[1].toInt()
            val payload = parts[3]

            val chunks = chunkBuffer.getOrPut(shortId) { mutableMapOf() }
            chunks[index] = payload
            chunkTotals[shortId] = total

            if (chunks.size == total) {
                // All chunks received — reconstruct the original JSON
                val fullBytes = (0 until total).flatMap { i ->
                    Base64.decode(chunks[i] ?: "", Base64.NO_WRAP).toList()
                }.toByteArray()
                val fullJson = String(fullBytes, Charsets.UTF_8)
                chunkBuffer.remove(shortId)
                chunkTotals.remove(shortId)
                dispatchToEngine(fullJson, endpointAddress)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chunk reassembly error: ${e.message}")
        }
    }

    private fun dispatchToEngine(json: String, endpointAddress: String?) {
        try {
            P2pMeshEngine.handleIncomingBleMessage(json, endpointAddress)
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching BLE message to engine: ${e.message}")
        }
    }
}
