package com.geomoby.blescanner.domain.repository

import com.geomoby.blescanner.domain.model.BleDevice
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for BLE beacon scanning operations.
 *
 * This interface lives in the domain layer and defines the contract
 * for BLE scanning without exposing any Android framework dependencies.
 * The data layer provides the concrete implementation via Hilt binding.
 *
 * Design rationale: By depending on this interface (not the implementation),
 * the ViewModel and any future use cases remain fully unit-testable
 * with mock repositories — no Android framework needed in tests.
 */
interface BeaconRepository {

    /**
     * Observable stream of currently discovered BLE devices.
     * The list is updated as new scan results arrive and sorted by signal strength.
     * Devices are keyed by MAC address — duplicate discoveries update the existing entry.
     */
    val discoveredDevices: Flow<List<BleDevice>>

    /**
     * Whether scanning is currently active.
     */
    val isScanning: Flow<Boolean>

    /**
     * Start BLE scanning. Results will be emitted via [discoveredDevices].
     * The caller is responsible for ensuring permissions are granted before calling this.
     */
    fun startScanning()

    /**
     * Stop BLE scanning and release scanner resources.
     */
    fun stopScanning()

    /**
     * Clear all previously discovered devices from the list.
     */
    fun clearDevices()
}
