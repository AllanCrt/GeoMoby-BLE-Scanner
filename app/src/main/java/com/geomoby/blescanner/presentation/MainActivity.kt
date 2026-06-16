package com.geomoby.blescanner.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.geomoby.blescanner.presentation.theme.BleScannerTheme
import com.geomoby.blescanner.presentation.ui.PermissionHandler
import com.geomoby.blescanner.presentation.ui.ScannerScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity entry point for the BLE Scanner application.
 *
 * Uses Jetpack Compose for the entire UI. The Activity's role is intentionally
 * minimal — it sets up the Compose content tree and delegates all business logic
 * and state management to [MainViewModel].
 *
 * ## Screen Navigation
 *
 * Uses a simple conditional check (not a navigation library) since the app
 * has only two states:
 * 1. **PermissionHandler**: Shown when required permissions are not granted.
 * 2. **ScannerScreen**: The main scanner UI, shown after permissions are granted.
 *
 * A full navigation library would be overkill for this two-state UI.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BleScannerTheme {
                val viewModel: MainViewModel = hiltViewModel()

                // Collect ViewModel state as Compose State for automatic recomposition
                val permissionsGranted by viewModel.permissionsGranted.collectAsState()
                val isScanning by viewModel.isScanning.collectAsState()
                val devices by viewModel.filteredDevices.collectAsState()
                val rssiThreshold by viewModel.rssiThreshold.collectAsState()

                if (permissionsGranted) {
                    ScannerScreen(
                        isScanning = isScanning,
                        devices = devices,
                        rssiThreshold = rssiThreshold,
                        onStartScan = viewModel::startScanning,
                        onStopScan = viewModel::stopScanning,
                        onRssiThresholdChange = viewModel::setRssiThreshold,
                        onClearDevices = viewModel::clearDevices
                    )
                } else {
                    PermissionHandler(
                        onPermissionsGranted = viewModel::onPermissionsResult
                    )
                }
            }
        }
    }
}
