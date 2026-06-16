package com.geomoby.blescanner.data.repository

import android.util.Log
import com.geomoby.blescanner.data.ble.BleScanner
import com.geomoby.blescanner.data.ble.IBeaconParser
import com.geomoby.blescanner.domain.model.BleDevice
import com.geomoby.blescanner.domain.repository.BeaconRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [BeaconRepository] that bridges the BLE data layer
 * with the domain layer.
 *
 * ## Responsibilities
 *
 * - Collects raw [ScanResult]s from [BleScanner] and converts them to [BleDevice] domain models.
 * - Aggregates devices by MAC address — new scan results update existing entries (deduplication).
 * - Delegates iBeacon frame parsing to [IBeaconParser].
 * - Automatically prunes stale devices that haven't been seen within [DEVICE_STALE_TIMEOUT_MS].
 * - Sorts the device list by RSSI (strongest signal first) for a natural UI ordering.
 *
 * ## Lifecycle
 *
 * This repository manages its own [CoroutineScope] with [SupervisorJob] to ensure
 * scan collection survives Activity configuration changes. The scope is tied to the
 * Singleton lifecycle (application process), not to any particular ViewModel.
 */
@Singleton
class BeaconRepositoryImpl @Inject constructor(
    private val bleScanner: BleScanner
) : BeaconRepository {

    companion object {
        private const val TAG = "BeaconRepository"

        /**
         * Maximum age for a device entry before it's considered stale.
         * Devices not seen within this window are removed from the list,
         * keeping the UI clean and reflecting only currently reachable beacons.
         */
        private const val DEVICE_STALE_TIMEOUT_MS = 30_000L
    }

    /** Dedicated scope for scan collection — survives config changes via Singleton lifecycle. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Active scan collection job, null when not scanning. */
    private var scanJob: Job? = null

    /** Thread-safe map of MAC address → BleDevice for deduplication and updates. */
    private val deviceMap = mutableMapOf<String, BleDevice>()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val discoveredDevices: Flow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    override val isScanning: Flow<Boolean> = bleScanner.isScanning

    override fun startScanning() {
        if (scanJob?.isActive == true) {
            Log.d(TAG, "Scan already active, ignoring duplicate start request")
            return
        }

        scanJob = scope.launch {
            bleScanner.scanResults(lowPowerMode = false)
                .catch { e ->
                    Log.e(TAG, "Scan flow error: ${e.message}", e)
                }
                .collect { scanResult ->
                    // Parse iBeacon data (returns null for non-iBeacon devices)
                    val iBeaconData = IBeaconParser.parse(scanResult)

                    // Map ScanResult → domain model
                    val device = BleDevice(
                        name = scanResult.device.name,
                        macAddress = scanResult.device.address,
                        rssi = scanResult.rssi,
                        iBeaconData = iBeaconData,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )

                    // Thread-safe update of the device map
                    synchronized(deviceMap) {
                        deviceMap[device.macAddress] = device
                        pruneStaleDevices()
                        _discoveredDevices.value = deviceMap.values
                            .sortedByDescending { it.rssi } // Strongest signal first
                            .toList()
                    }
                }
        }

        Log.d(TAG, "Scanning started")
    }

    override fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        bleScanner.forceStop()
        Log.d(TAG, "Scanning stopped")
    }

    override fun clearDevices() {
        synchronized(deviceMap) {
            deviceMap.clear()
            _discoveredDevices.value = emptyList()
        }
        Log.d(TAG, "Device list cleared")
    }

    /**
     * Removes devices that haven't been seen within [DEVICE_STALE_TIMEOUT_MS].
     *
     * This prevents the list from accumulating devices that have moved out of range,
     * providing a more accurate real-time view of the BLE environment.
     *
     * Must be called from within a `synchronized(deviceMap)` block.
     */
    private fun pruneStaleDevices() {
        val now = System.currentTimeMillis()
        val staleEntries = deviceMap.entries.filter { (_, device) ->
            now - device.lastSeenTimestamp > DEVICE_STALE_TIMEOUT_MS
        }
        staleEntries.forEach { deviceMap.remove(it.key) }
    }
}
