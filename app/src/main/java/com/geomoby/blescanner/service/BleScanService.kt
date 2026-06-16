package com.geomoby.blescanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.geomoby.blescanner.R
import com.geomoby.blescanner.domain.repository.BeaconRepository
import com.geomoby.blescanner.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground Service that keeps BLE scanning alive when the app is backgrounded
 * or the screen is turned off.
 *
 * ## Why a Foreground Service?
 *
 * Android imposes strict background execution limits starting from Android 8 (Oreo).
 * Without a foreground service, BLE scans are killed within minutes when the app
 * is not visible. A foreground service with a persistent notification is the
 * recommended mechanism for continuous background BLE operations.
 *
 * ## Key Design Decisions
 *
 * - **foregroundServiceType="connectedDevice"**: Mandatory on Android 14+ (API 34)
 *   for any service that interacts with Bluetooth devices. Without this, the system
 *   throws a [MissingForegroundServiceTypeException].
 *
 * - **IMPORTANCE_LOW notification**: Minimizes user intrusion (no sound/vibration)
 *   while keeping the notification visible. Using IMPORTANCE_MIN would trigger a
 *   system battery usage warning on some devices.
 *
 * - **START_STICKY**: If the system kills this service under memory pressure, Android
 *   will restart it automatically. This improves scanning reliability.
 *
 * - **Shared BleScanner via Hilt**: The service uses the same [BeaconRepository]
 *   singleton as the UI layer, so scan results flow seamlessly to the screen
 *   whether the service or the Activity initiated the scan.
 *
 * ## Known Limitations
 *
 * - **OEM battery optimization**: Xiaomi MIUI, Samsung OneUI, Huawei EMUI, and other
 *   custom ROMs may still kill foreground services. Users may need to whitelist the
 *   app in their device's battery settings.
 *
 * - **Doze mode**: On stationary, unplugged devices, Android's Doze mode can delay
 *   scan result delivery even with a foreground service active.
 *
 * - **Unfiltered scan timeout**: Android may silently stop unfiltered BLE scans after
 *   ~30 minutes. For production use, a scan restart timer should be implemented.
 */
@AndroidEntryPoint
class BleScanService : Service() {

    companion object {
        private const val TAG = "BleScanService"
        private const val CHANNEL_ID = "ble_scan_channel"
        private const val NOTIFICATION_ID = 1

        /** Intent action to start scanning. */
        const val ACTION_START = "com.geomoby.blescanner.action.START_SCAN"

        /** Intent action to stop scanning. */
        const val ACTION_STOP = "com.geomoby.blescanner.action.STOP_SCAN"

        /**
         * Starts the BLE scanning foreground service.
         *
         * Uses [startForegroundService] on Android 8+ as required by the platform.
         * The service must call [startForeground] within 5 seconds of this call.
         */
        fun start(context: Context) {
            val intent = Intent(context, BleScanService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the BLE scanning foreground service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, BleScanService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var beaconRepository: BeaconRepository

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Starting foreground scan service")
                startForegroundWithNotification()
                beaconRepository.startScanning()
            }

            ACTION_STOP -> {
                Log.d(TAG, "Stopping foreground scan service")
                beaconRepository.stopScanning()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            else -> {
                Log.w(TAG, "Received unknown action: ${intent?.action}")
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        beaconRepository.stopScanning()
        Log.d(TAG, "BleScanService destroyed")
    }

    /**
     * Promotes this service to foreground with a persistent notification.
     *
     * On Android 14+ (API 34), the foreground service type must be explicitly
     * passed to [startForeground] in addition to being declared in the manifest.
     */
    private fun startForegroundWithNotification() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: Must specify foreground service type explicitly at runtime
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Builds the persistent notification displayed while scanning is active.
     * Tapping the notification opens the main scanner screen.
     */
    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE Scanner Active")
            .setContentText("Scanning for nearby beacons...")
            .setSmallIcon(R.drawable.ic_bluetooth_searching)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * Creates the notification channel required on Android 8+ (API 26).
     *
     * Uses [NotificationManager.IMPORTANCE_LOW] — the notification is visible
     * but produces no sound or vibration, minimizing user disruption during
     * continuous background scanning.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE Scanning Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when BLE beacon scanning is active in the background"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
