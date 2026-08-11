package com.scream.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE GATT Client for the SCREAM mesh.
 *
 * For each SCREAM device discovered via BLE scan, this client:
 *   1. Connects to the remote GATT server
 *   2. Discovers the SCREAM Mesh Service
 *   3. Subscribes to the NOTIFY characteristic (incoming messages)
 *   4. Exposes the WRITE characteristic (outgoing messages)
 *   5. Auto-reconnects with exponential back-off on disconnection
 *
 * Message chunking mirrors the server's protocol:
 *   Single packet  — raw JSON bytes (≤ 180 bytes)
 *   Multi packet   — "SCHK:<shortId>:<i>/<n>:<base64Data>"
 */
object BleGattClient {
    private const val TAG = "BleGattClient"
    private const val CHUNK_RAW_SIZE = 120 // raw bytes per BLE packet before base64
    private const val RECONNECT_DELAY_MS = 5_000L
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // address -> active GATT connection
    private val activeGatts = ConcurrentHashMap<String, BluetoothGatt>()
    // address -> write characteristic (ready to send)
    private val writeChars = ConcurrentHashMap<String, BluetoothGattCharacteristic>()
    // address -> outgoing message queue (held until services are discovered)
    private val pendingQueues = ConcurrentHashMap<String, ArrayDeque<String>>()
    // shortId -> (index -> base64 chunk)
    private val chunkBuffer = ConcurrentHashMap<String, MutableMap<Int, String>>()
    // shortId -> totalChunks
    private val chunkTotals = ConcurrentHashMap<String, Int>()
    // devices we are trying to connect to (prevent duplicate attempts)
    private val pendingConnections = ConcurrentHashMap.newKeySet<String>()
    // per-device reconnect attempt counter for back-off
    private val reconnectAttempts = ConcurrentHashMap<String, Int>()
    @Volatile private var connectionGeneration = 0L

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Attempt a GATT connection to a discovered BLE device.
     * No-ops if we are already connected or currently connecting.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(context: Context, device: BluetoothDevice) {
        val address = device.address
        if (activeGatts.containsKey(address) || pendingConnections.contains(address)) return

        Log.d(TAG, "Initiating connection to $address")
        pendingConnections.add(address)

        try {
            val callback = buildCallback(context, device)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, callback)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to connect: ${e.message}")
            pendingConnections.remove(address)
        } catch (e: Exception) {
            Log.e(TAG, "connectGatt failed for $address: ${e.message}")
            pendingConnections.remove(address)
        }
    }

    /**
     * Send a JSON message to all currently connected SCREAM peers.
     * Messages are chunked if they exceed the safe BLE MTU.
     */
    @SuppressLint("MissingPermission")
    fun sendMessage(jsonString: String) {
        activeGatts.keys.toList().forEach { address ->
            val gatt = activeGatts[address] ?: return@forEach
            val writeChar = writeChars[address]
            if (writeChar == null) {
                // Not yet service-discovered — queue the message
                pendingQueues.getOrPut(address) { ArrayDeque() }.addLast(jsonString)
            } else {
                deliverMessage(gatt, writeChar, jsonString)
            }
        }
    }

    /** Disconnect all open GATT connections and clean up state. */
    @SuppressLint("MissingPermission")
    fun disconnectAll() {
        connectionGeneration++
        activeGatts.values.toList().forEach { gatt ->
            try { gatt.disconnect(); gatt.close() } catch (e: Exception) { }
        }
        activeGatts.clear()
        writeChars.clear()
        pendingQueues.clear()
        pendingConnections.clear()
        chunkBuffer.clear()
        chunkTotals.clear()
        reconnectAttempts.clear()
        Log.d(TAG, "All GATT connections closed")
    }

    fun getConnectedCount(): Int = activeGatts.size

    // ──────────────────────────────────────────────────────────────────────────
    // GATT callbacks — one instance per device
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun buildCallback(context: Context, device: BluetoothDevice): BluetoothGattCallback {
        val address = device.address

        return object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Connected to $address (status=$status)")
                        activeGatts[address] = gatt
                        pendingConnections.remove(address)
                        reconnectAttempts[address] = 0
                        // Request higher MTU for fewer chunks needed
                        try { gatt.requestMtu(512) } catch (e: Exception) {
                            gatt.discoverServices()
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Disconnected from $address (status=$status)")
                        cleanupDevice(address, gatt)
                        scheduleReconnect(context, device)
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "MTU changed to $mtu for $address")
                try { gatt.discoverServices() } catch (e: Exception) {
                    Log.e(TAG, "discoverServices failed: ${e.message}")
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Service discovery failed for $address, status=$status")
                    return
                }

                val service = gatt.getService(BleGattServer.SCREAM_SERVICE_UUID)
                if (service == null) {
                    Log.d(TAG, "$address is not a SCREAM device (no mesh service) — disconnecting")
                    try { gatt.disconnect() } catch (e: Exception) { }
                    return
                }

                // Subscribe to incoming notifications
                val notifyChar = service.getCharacteristic(BleGattServer.NOTIFY_CHAR_UUID)
                if (notifyChar != null) {
                    try {
                        gatt.setCharacteristicNotification(notifyChar, true)
                        val descriptor = notifyChar.getDescriptor(CCCD_UUID)
                        if (descriptor != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                @Suppress("DEPRECATION")
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to subscribe to notifications on $address: ${e.message}")
                    }
                }

                // Store the write characteristic
                val writeChar = service.getCharacteristic(BleGattServer.WRITE_CHAR_UUID)
                if (writeChar != null) {
                    writeChars[address] = writeChar
                    Log.d(TAG, "SCREAM mesh service ready on $address — triggering immediate P2P mesh sync")
                    flushPendingMessages(gatt, writeChar, address)
                    P2pMeshEngine.triggerImmediateSyncWithPeer(address)
                }
            }

            // Android 13+ callback
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                processIncoming(String(value, Charsets.UTF_8))
            }

            // Legacy callback (Android < 13)
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                val value = characteristic?.value ?: return
                processIncoming(String(value, Charsets.UTF_8))
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Message delivery (with chunking)
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun deliverMessage(
        gatt: BluetoothGatt,
        writeChar: BluetoothGattCharacteristic,
        jsonString: String
    ) {
        val bytes = jsonString.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 180) {
            writePacket(gatt, writeChar, bytes)
        } else {
            // Chunk into multiple packets
            scope.launch {
                val shortId = UUID.randomUUID().toString().take(8)
                val rawChunks = bytes.toList().chunked(CHUNK_RAW_SIZE)
                val total = rawChunks.size
                rawChunks.forEachIndexed { index, chunk ->
                    val encoded = Base64.encodeToString(chunk.toByteArray(), Base64.NO_WRAP)
                    val packet = "SCHK:$shortId:$index/$total:$encoded".toByteArray(Charsets.UTF_8)
                    writePacket(gatt, writeChar, packet)
                    delay(30) // brief pacing so the remote BLE stack can keep up
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writePacket(gatt: BluetoothGatt, writeChar: BluetoothGattCharacteristic, bytes: ByteArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(writeChar, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                writeChar.value = bytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(writeChar)
            }
        } catch (e: Exception) {
            Log.w(TAG, "writePacket failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun flushPendingMessages(
        gatt: BluetoothGatt,
        writeChar: BluetoothGattCharacteristic,
        address: String
    ) {
        val queue = pendingQueues.remove(address) ?: return
        queue.forEach { msg -> deliverMessage(gatt, writeChar, msg) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Incoming message handling + chunk reassembly
    // ──────────────────────────────────────────────────────────────────────────

    private fun processIncoming(text: String) {
        if (text.startsWith("SCHK:")) {
            reassembleChunk(text)
        } else {
            dispatchToEngine(text)
        }
    }

    private fun reassembleChunk(text: String) {
        try {
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
                val fullBytes = (0 until total).flatMap { i ->
                    Base64.decode(chunks[i] ?: "", Base64.NO_WRAP).toList()
                }.toByteArray()
                chunkBuffer.remove(shortId)
                chunkTotals.remove(shortId)
                dispatchToEngine(String(fullBytes, Charsets.UTF_8))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chunk reassembly error: ${e.message}")
        }
    }

    private fun dispatchToEngine(json: String) {
        try {
            P2pMeshEngine.handleIncomingBleMessage(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching to P2pMeshEngine: ${e.message}")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Connection lifecycle helpers
    // ──────────────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun cleanupDevice(address: String, gatt: BluetoothGatt) {
        activeGatts.remove(address)
        writeChars.remove(address)
        pendingConnections.remove(address)
        try { gatt.close() } catch (e: Exception) { }
    }

    private fun scheduleReconnect(context: Context, device: BluetoothDevice) {
        val address = device.address
        val generation = connectionGeneration
        val attempts = reconnectAttempts.getOrDefault(address, 0)
        // Exponential back-off: 5s, 10s, 20s, capped at 120s
        val delay = minOf(RECONNECT_DELAY_MS * (1L shl minOf(attempts, 6)), 120_000L)
        reconnectAttempts[address] = attempts + 1

        Log.d(TAG, "Scheduling reconnect to $address in ${delay}ms (attempt #${attempts + 1})")
        scope.launch {
            delay(delay)
            if (generation == connectionGeneration && !activeGatts.containsKey(address)) {
                connectToDevice(context, device)
            }
        }
    }
}
