package com.geomoby.blescanner.data.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level BLE scanner wrapper that bridges Android's callback-based
 * [BluetoothLeScanner] API into reactive Kotlin Flows.
 *
 * ## Design Decisions
 *
 * - **callbackFlow**: Converts the callback-based [ScanCallback] into a cold [Flow].
 *   The scan starts when the Flow is collected and automatically stops when the
 *   collector cancels (coroutine scope cancellation). This ties cleanup to the
 *   coroutine lifecycle, preventing resource leaks.
 *
 * - **Singleton scope**: BLE scanning operations are global to the device. Only one
 *   active scan should exist at a time. Hilt manages the singleton lifecycle.
 *
 * - **Power modes**: Supports [ScanSettings.SCAN_MODE_LOW_LATENCY] for responsive
 *   foreground scanning and [ScanSettings.SCAN_MODE_LOW_POWER] for battery-efficient
 *   background scanning. The repository chooses the mode based on app state.
 *
 * - **Result batching**: Uses [REPORT_DELAY_MS] to batch scan results, reducing
 *   CPU wakeups and improving battery life — critical for a geolocation app.
 *
 * ## Known Android Quirks
 *
 * - Unfiltered scans (no [ScanFilter]) may be silently killed after ~30 minutes.
 *   The repository layer should implement a restart cycle if long-running scans
 *   are needed.
 * - Some OEM ROMs (Xiaomi MIUI, Samsung OneUI) aggressively throttle BLE scans
 *   even with a foreground service.
 */
@Singleton
class BleScanner @Inject constructor(
    private val bluetoothManager: BluetoothManager
) {

    companion object {
        private const val TAG = "BleScanner"

        /**
         * Batch result delivery delay in milliseconds.
         * A 1-second delay provides near-real-time results while being power-efficient.
         * Setting this to 0 delivers results immediately but increases battery drain.
         */
        private const val REPORT_DELAY_MS = 1000L
    }

    private val _isScanning = MutableStateFlow(false)

    /** Observable scanning state. */
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** Reference to the currently active callback, used for force-stopping. */
    private var currentCallback: ScanCallback? = null

    /**
     * Creates a cold Flow that emits [ScanResult]s from BLE scanning.
     *
     * The scan starts when the Flow is collected and stops when the collector
     * cancels. This ensures automatic cleanup tied to the coroutine lifecycle.
     *
     * @param lowPowerMode If true, uses [ScanSettings.SCAN_MODE_LOW_POWER] suitable for
     *                     background scanning. If false, uses [ScanSettings.SCAN_MODE_LOW_LATENCY]
     *                     for responsive foreground scanning.
     * @return A Flow emitting individual [ScanResult]s as devices are discovered.
     */
    fun scanResults(lowPowerMode: Boolean = false): Flow<ScanResult> = callbackFlow {
        val scanner: BluetoothLeScanner? = bluetoothManager.adapter?.bluetoothLeScanner

        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner not available. Is Bluetooth enabled?")
            close()
            return@callbackFlow
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(
                if (lowPowerMode) ScanSettings.SCAN_MODE_LOW_POWER
                else ScanSettings.SCAN_MODE_LOW_LATENCY
            )
            .setReportDelay(REPORT_DELAY_MS)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Called when reportDelay is 0 (immediate delivery).
                trySend(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                // Called when reportDelay > 0 (batched delivery).
                // Each result in the batch is emitted individually for uniform downstream processing.
                results.forEach { trySend(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with error code: $errorCode (${errorCodeToString(errorCode)})")
                close(ScanFailedException(errorCode))
            }
        }

        currentCallback = callback
        _isScanning.value = true

        try {
            // Start scan with no filters — we want to discover ALL BLE devices.
            // For a generic beacon scanner, we can't pre-filter by Service UUID
            // because we don't know what devices will be nearby.
            scanner.startScan(null, scanSettings, callback)
            Log.d(TAG, "BLE scan started (lowPowerMode=$lowPowerMode, reportDelay=${REPORT_DELAY_MS}ms)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLE scan permissions", e)
            _isScanning.value = false
            close(e)
            return@callbackFlow
        }

        // awaitClose is called when the Flow collector cancels.
        // This is the cleanup hook — we stop the scan and release resources.
        awaitClose {
            Log.d(TAG, "BLE scan stopping (Flow cancelled)")
            _isScanning.value = false
            currentCallback = null
            try {
                scanner.stopScan(callback)
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing permissions while stopping scan", e)
            }
        }
    }

    /**
     * Forcefully stops any ongoing scan.
     *
     * This is used by the foreground service when the user explicitly stops scanning.
     * Unlike Flow cancellation (which happens via scope), this provides an imperative
     * way to halt the scan immediately.
     */
    fun forceStop() {
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner ?: return
        currentCallback?.let { callback ->
            try {
                scanner.stopScan(callback)
                Log.d(TAG, "BLE scan force-stopped")
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing permissions while force-stopping scan", e)
            }
            currentCallback = null
            _isScanning.value = false
        }
    }

    /**
     * Converts a [ScanCallback] error code to a human-readable string for logging.
     */
    private fun errorCodeToString(errorCode: Int): String = when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REGISTRATION_FAILED"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
        else -> "UNKNOWN($errorCode)"
    }
}

/**
 * Exception wrapping BLE scan failure error codes from [ScanCallback.onScanFailed].
 *
 * @property errorCode The platform-specific error code.
 * @see ScanCallback.SCAN_FAILED_ALREADY_STARTED
 * @see ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED
 * @see ScanCallback.SCAN_FAILED_INTERNAL_ERROR
 * @see ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED
 */
class ScanFailedException(val errorCode: Int) :
    Exception("BLE scan failed with error code: $errorCode")
