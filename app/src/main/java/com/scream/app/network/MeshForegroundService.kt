package com.scream.app.network

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.scream.app.MainActivity
import com.scream.app.data.ScreamRepository
import com.scream.app.identity.UserPreferencesRepository
import com.scream.app.identity.dataStore
import com.scream.app.model.BackgroundMode
import com.scream.app.model.NetworkStatus
import com.scream.app.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * MeshForegroundService — the heartbeat of the SCREAM P2P mesh.
 *
 * Runs as a persistent foreground service so Android does not kill it when the
 * user closes the app. It:
 *   - Starts BLE advertising (so other SCREAM devices can discover us)
 *   - Starts BLE scanning (so we discover them)
 *   - Opens the GATT server (so peers can connect and push messages)
 *   - Keeps the P2P TCP/UDP engine running (for LAN fallback)
 *
 * Lifecycle:
 *   Started by [BluetoothBootReceiver] when BT turns ON or device boots.
 *   Also started by [MainActivity] on app launch (idempotent — safe to call multiple times).
 *   Declared with android:stopWithTask="false" so it survives app swipe-close.
 *   Returns START_STICKY so the system restarts it if killed under memory pressure.
 */
class MeshForegroundService : Service() {

    companion object {
        private const val TAG = "MeshForegroundService"
        private const val NOTIFICATION_ID = 7331
        private const val CHANNEL_ID = "scream_mesh_channel"

        @Volatile
        private var isServiceRunning = false

        fun isRunning(): Boolean = isServiceRunning
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var meshLifecycleJob: Job? = null

    // ──────────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    private var isBluetoothOn = true

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        isBluetoothOn = false
                        Log.d(TAG, "Bluetooth turned OFF — updating notification text")
                        updateNotificationState(
                            title = "SCREAM Mesh Paused",
                            contentText = "Bluetooth is turned off — discovery paused. Turn on Bluetooth to search for nearby users."
                        )
                        MeshNetworkManager.stop()
                        BleGattServer.stop()
                    }
                    BluetoothAdapter.STATE_ON -> {
                        isBluetoothOn = true
                        Log.d(TAG, "Bluetooth turned ON — resuming mesh & updating notification")
                        updateNotificationState(
                            title = "SCREAM Mesh Active",
                            contentText = "Scanning for nearby users via Bluetooth…"
                        )
                        bootMesh()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        Log.d(TAG, "MeshForegroundService created")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        try {
            registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register bluetoothStateReceiver: ${e.message}")
        }
        bootMesh()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-boot if something stopped the mesh mid-life
        if (!MeshNetworkManager.isRunning()) {
            bootMesh()
        }
        scheduleDozeWakeupPulse()
        return START_STICKY
    }

    private fun scheduleDozeWakeupPulse() {
        try {
            val alarmManager = getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(this, MeshForegroundService::class.java)
            val pendingIntent = PendingIntent.getService(
                this, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = SystemClock.elapsedRealtime() + 15 * 60 * 1000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule Doze wakeup pulse: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "App task removed — mesh service continues running")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        meshLifecycleJob?.cancel()
        meshLifecycleJob = null
        serviceScope.coroutineContext.cancelChildren()
        Log.d(TAG, "MeshForegroundService destroyed — tearing down mesh")

        try { unregisterReceiver(bluetoothStateReceiver) } catch (e: Exception) {}
        BleGattServer.stop()
        MeshNetworkManager.stop()
        P2pMeshEngine.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ──────────────────────────────────────────────────────────────────────────
    // Mesh initialisation & Lifecycle Observation
    // ──────────────────────────────────────────────────────────────────────────

    private fun bootMesh() {
        if (meshLifecycleJob?.isActive == true) return

        meshLifecycleJob = serviceScope.launch {
            try {
                // Repository must be initialised before anything else
                ScreamRepository.init(applicationContext)

                val userPrefs = UserPreferencesRepository(applicationContext.dataStore)
                
                // Collect background mode & offline updates reactively
                userPrefs.userProfileFlow.collectLatest { profile ->
                    val isPermanentOffline = profile.isPermanentOffline

                    // Check low battery threshold
                    val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 100
                    val isCharging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING

                    val autoDeepSleepTriggered = profile.isAutoDeepSleepEnabled && !isCharging && batteryPct <= profile.autoDeepSleepThreshold

                    val effectiveMode = when {
                        isPermanentOffline -> BackgroundMode.DISABLED
                        autoDeepSleepTriggered -> BackgroundMode.DEEP_SLEEP
                        else -> profile.backgroundMode
                    }

                    Log.d(TAG, "Observed BackgroundMode change: $effectiveMode (Offline: $isPermanentOffline, AutoSleep: $autoDeepSleepTriggered)")

                    when (effectiveMode) {
                        BackgroundMode.ACTIVE -> {
                            val user: User? = if (profile.isRegistered && profile.uuid.isNotBlank()) {
                                User(
                                    id = if (profile.uuid.length >= 4)
                                        "#" + profile.uuid.take(4).uppercase()
                                    else
                                        "#0000",
                                    alias = profile.alias.ifBlank { "Anonymous" },
                                    avatar = profile.emojiAvatar.ifBlank { "😎" },
                                    age = profile.age,
                                    gender = profile.gender
                                )
                            } else null

                            MeshNetworkManager.start(applicationContext)
                            BleGattServer.start(applicationContext)

                            if (user != null) {
                                P2pMeshEngine.start(user)
                                Log.d(TAG, "Full mesh started for user: ${user.alias}")
                            } else {
                                Log.d(TAG, "No registered user — BLE advertising only until registration")
                            }

                            ScreamRepository.updateNetworkStatus(NetworkStatus.ACTIVE)
                            updateNotificationState(
                                title = "SCREAM Mesh Active",
                                contentText = "Scanning for nearby peers via Bluetooth & P2P…"
                            )
                        }

                        BackgroundMode.DEEP_SLEEP -> {
                            Log.d(TAG, "Enabling Deep Sleep mode — pausing mesh networking")
                            MeshNetworkManager.stop()
                            BleGattServer.stop()
                            P2pMeshEngine.stop()
                            ScreamRepository.updateNetworkStatus(NetworkStatus.OFFLINE)

                            val reasonText = if (autoDeepSleepTriggered)
                                "Low battery ($batteryPct%) — background networking paused."
                            else
                                "Background networking is disabled to save battery."

                            updateNotificationState(
                                title = "SCREAM is in Deep Sleep",
                                contentText = reasonText
                            )
                        }

                        BackgroundMode.DISABLED -> {
                            Log.d(TAG, "Disabling mesh networking completely")
                            MeshNetworkManager.stop()
                            BleGattServer.stop()
                            P2pMeshEngine.stop()
                            ScreamRepository.updateNetworkStatus(NetworkStatus.OFFLINE)

                            val reasonText = if (isPermanentOffline)
                                "Networking is turned OFF by user setting."
                            else
                                "Networking is completely disabled."

                            updateNotificationState(
                                title = "SCREAM is Offline",
                                contentText = reasonText
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in mesh lifecycle collector: ${e.message}")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SCREAM Mesh Network",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the SCREAM peer mesh active for offline communication"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("SCREAM Mesh Active")
                .setContentText("Scanning for nearby peers via Bluetooth…")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(openAppIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("SCREAM Mesh Active")
                .setContentText("Scanning for nearby peers via Bluetooth…")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(openAppIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotificationState(title: String, contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(if (isBluetoothOn) android.R.drawable.stat_sys_data_bluetooth else android.R.drawable.stat_notify_error)
                .setContentIntent(openAppIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(if (isBluetoothOn) android.R.drawable.stat_sys_data_bluetooth else android.R.drawable.stat_notify_error)
                .setContentIntent(openAppIntent)
                .setOngoing(true)
                .build()
        }

        manager.notify(NOTIFICATION_ID, notification)
    }
}
