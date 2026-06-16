package com.geomoby.blescanner.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geomoby.blescanner.domain.model.BleDevice
import com.geomoby.blescanner.domain.repository.BeaconRepository
import com.geomoby.blescanner.service.BleScanService
import com.geomoby.blescanner.util.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the main scanner screen.
 *
 * ## Responsibilities
 *
 * - **Scanning lifecycle**: Starts/stops scanning by controlling the foreground service,
 *   which ensures scanning continues when the app is backgrounded.
 *
 * - **RSSI filtering**: Combines the device list with the threshold to produce a
 *   filtered view using Flow operators. This is reactive: changing the slider
 *   instantly updates the visible list without triggering a new scan.
 *
 * - **Permission tracking**: Monitors whether all required permissions are granted
 *   to drive the UI between the permission screen and the scanner screen.
 *
 * ## Architecture Notes
 *
 * - Uses [AndroidViewModel] for Application context access (needed to start the
 *   foreground service). All other dependencies are injected via Hilt.
 *
 * - The ViewModel intentionally does NOT stop scanning in [onCleared]. The foreground
 *   service manages its own lifecycle, allowing scanning to continue during
 *   configuration changes (screen rotation).
 *
 * - [filteredDevices] uses [SharingStarted.WhileSubscribed] with a 5-second stop delay
 *   to survive brief collector interruptions (e.g., fast navigation).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val beaconRepository: BeaconRepository
) : AndroidViewModel(application) {

    /** RSSI threshold for filtering. -100 = show all devices (effectively OFF). */
    private val _rssiThreshold = MutableStateFlow(-100)
    val rssiThreshold: StateFlow<Int> = _rssiThreshold.asStateFlow()

    /** Whether BLE scanning is currently active. */
    val isScanning: StateFlow<Boolean> = beaconRepository.isScanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Whether all required permissions are granted. */
    private val _permissionsGranted = MutableStateFlow(
        PermissionUtils.hasAllPermissions(application)
    )
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    /**
     * Filtered list of discovered devices.
     *
     * Uses the `combine` operator to reactively merge the device list with
     * the RSSI threshold. When either changes, the filtered list is recomputed.
     *
     * This approach ensures:
     * - No data duplication (the underlying list is the single source of truth)
     * - Instant filter response (no rescan needed)
     * - Efficient recomposition (Compose only recomposes changed items)
     */
    val filteredDevices: StateFlow<List<BleDevice>> = combine(
        beaconRepository.discoveredDevices,
        _rssiThreshold
    ) { devices, threshold ->
        if (threshold <= -100) devices
        else devices.filter { it.rssi >= threshold }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Start BLE scanning via the foreground service.
     *
     * The foreground service ensures scanning persists when:
     * - The screen is off
     * - The app is in the background (user pressed Home)
     * - The system applies background execution limits
     */
    fun startScanning() {
        BleScanService.start(getApplication())
    }

    /**
     * Stop BLE scanning and dismiss the foreground service notification.
     */
    fun stopScanning() {
        BleScanService.stop(getApplication())
    }

    /**
     * Update the RSSI filter threshold.
     *
     * @param threshold New threshold in dBm. Use -100 to disable filtering (show all).
     */
    fun setRssiThreshold(threshold: Int) {
        _rssiThreshold.value = threshold
    }

    /**
     * Clear all discovered devices from the list.
     */
    fun clearDevices() {
        beaconRepository.clearDevices()
    }

    /**
     * Called when the permission request flow completes.
     * Re-checks all permissions and updates the state to drive UI transitions.
     */
    fun onPermissionsResult() {
        _permissionsGranted.value = PermissionUtils.hasAllPermissions(getApplication())
    }
}
