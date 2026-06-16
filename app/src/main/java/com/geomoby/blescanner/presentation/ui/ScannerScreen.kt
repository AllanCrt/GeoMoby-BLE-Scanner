package com.geomoby.blescanner.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.geomoby.blescanner.domain.model.BleDevice
import com.geomoby.blescanner.presentation.theme.SurfaceDark
import com.geomoby.blescanner.presentation.theme.TextAccent

/**
 * Main scanner screen — the single screen of the BLE Scanner application.
 *
 * ## Features
 * - Top app bar with pulsing scanning indicator and device count
 * - Collapsible RSSI filter slider (toggled via filter icon)
 * - Scrollable LazyColumn of discovered BLE devices
 * - FAB to start/stop scanning (changes color and icon based on state)
 * - Animated empty state when no devices are found
 *
 * ## UI Hierarchy
 * ```
 * Scaffold
 * ├── TopAppBar (title + pulsing dot + filter toggle + clear button)
 * ├── RssiFilterSlider (collapsible)
 * ├── LazyColumn (device list) OR EmptyState
 * └── FAB (start/stop scan)
 * ```
 *
 * @param isScanning Whether BLE scanning is currently active.
 * @param devices Filtered list of discovered BLE devices.
 * @param rssiThreshold Current RSSI filter threshold in dBm.
 * @param onStartScan Callback to start BLE scanning.
 * @param onStopScan Callback to stop BLE scanning.
 * @param onRssiThresholdChange Callback when user adjusts RSSI filter.
 * @param onClearDevices Callback to clear all discovered devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    isScanning: Boolean,
    devices: List<BleDevice>,
    rssiThreshold: Int,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onRssiThresholdChange: (Int) -> Unit,
    onClearDevices: () -> Unit
) {
    var showFilter by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BLE Scanner")
                        if (isScanning) {
                            Spacer(modifier = Modifier.width(12.dp))
                            PulsingDot()
                        }
                    }
                },
                actions = {
                    // Device count badge
                    if (devices.isNotEmpty()) {
                        Text(
                            text = "${devices.size}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextAccent,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    // Clear devices button
                    if (devices.isNotEmpty()) {
                        IconButton(onClick = onClearDevices) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Devices",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // RSSI filter toggle
                    IconButton(onClick = { showFilter = !showFilter }) {
                        Icon(
                            imageVector = Icons.Default.FilterAlt,
                            contentDescription = "Toggle RSSI Filter",
                            tint = if (showFilter || rssiThreshold > -100)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (isScanning) onStopScan() else onStartScan() },
                containerColor = if (isScanning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            ) {
                AnimatedContent(
                    targetState = isScanning,
                    label = "fab_icon"
                ) { scanning ->
                    Icon(
                        imageVector = if (scanning) Icons.Default.Stop
                        else Icons.Default.PlayArrow,
                        contentDescription = if (scanning) "Stop Scan" else "Start Scan"
                    )
                }
            }
        },
        containerColor = SurfaceDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Collapsible RSSI filter panel
            RssiFilterSlider(
                threshold = rssiThreshold,
                onThresholdChange = onRssiThresholdChange,
                isVisible = showFilter,
                deviceCount = devices.size
            )

            // Device list or empty state
            if (devices.isEmpty()) {
                EmptyState(isScanning = isScanning)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = devices,
                        key = { it.macAddress } // Stable keys for efficient recomposition
                    ) { device ->
                        BeaconListItem(device = device)
                    }
                }
            }
        }
    }
}

/**
 * Pulsing green dot indicating active scanning.
 * Smoothly oscillates between full and low opacity to create a "heartbeat" effect.
 */
@Composable
private fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsing_alpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary)
    )
}

/**
 * Empty state shown when no devices have been discovered yet.
 *
 * Features an animated Bluetooth icon:
 * - Pulsing when scanning is active (indicates "searching...")
 * - Static and dimmed when scanning is stopped (invites user to start)
 */
@Composable
private fun EmptyState(isScanning: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val iconAlpha = if (isScanning) {
            val infiniteTransition = rememberInfiniteTransition(label = "empty_pulsing")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "empty_icon_alpha"
            )
            alpha
        } else {
            0.3f
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .alpha(iconAlpha),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isScanning) "Searching for beacons..."
            else "Tap the button to start scanning",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
