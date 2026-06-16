package com.geomoby.blescanner.domain.model

/**
 * Domain model representing a discovered BLE device.
 *
 * This model is framework-agnostic and contains the core data for any BLE device
 * found during scanning, regardless of whether it's an iBeacon or a generic
 * BLE peripheral.
 *
 * @property name Device advertised name, null if the device is not broadcasting a name.
 * @property macAddress Hardware MAC address (e.g., "AA:BB:CC:DD:EE:FF").
 * @property rssi Received Signal Strength Indicator in dBm (typically -100 to 0).
 *               Higher values indicate stronger signal / closer proximity.
 * @property iBeaconData Parsed iBeacon payload, null for non-iBeacon devices.
 * @property lastSeenTimestamp Epoch millis of the last scan result for this device.
 *                             Used for pruning stale entries that have moved out of range.
 */
data class BleDevice(
    val name: String?,
    val macAddress: String,
    val rssi: Int,
    val iBeaconData: IBeaconData? = null,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
