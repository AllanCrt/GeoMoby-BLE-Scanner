package com.geomoby.blescanner.presentation.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.geomoby.blescanner.util.PermissionUtils

/**
 * Composable that handles the BLE permission request flow.
 *
 * Displays a rationale screen explaining why permissions are needed,
 * with a prominent button to trigger the system permission dialog.
 * Once all permissions are granted, it invokes [onPermissionsGranted]
 * to transition to the main scanner screen.
 *
 * ## Permission States
 *
 * Handles three distinct states:
 * 1. **Initial / Denied**: Shows rationale and a "Grant Permissions" button
 *    that triggers the system permission dialog.
 * 2. **Permanently Denied**: The user previously selected "Don't ask again"
 *    on at least one permission. The system dialog will no longer appear,
 *    so we show an "Open Settings" button that redirects to the app's
 *    system settings page where permissions can be granted manually.
 *
 * ## Return from Settings
 *
 * When the user navigates to Settings and returns, a [LifecycleEventObserver]
 * on [Lifecycle.Event.ON_RESUME] automatically re-checks permissions and
 * transitions to the scanner screen if all are now granted.
 *
 * @param onPermissionsGranted Callback invoked when all required permissions are granted.
 */
@Composable
fun PermissionHandler(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val permissions = PermissionUtils.getRequiredPermissions()

    var permanentlyDenied by remember { mutableStateOf(false) }

    // Re-check permissions when returning from Settings (or any activity resume).
    // This ensures seamless transition after the user manually grants permissions
    // from the system settings page.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (PermissionUtils.hasAllPermissions(context)) {
                    onPermissionsGranted()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            // Detect "permanently denied" state.
            // After a permission request, shouldShowRequestPermissionRationale returns:
            //   true  → user denied but can still be asked again
            //   false → user selected "Don't ask again" (permanently denied)
            // Since we're inside the result callback (permission was just requested),
            // false unambiguously means "Don't ask again" — not "never requested".
            val anyPermanentlyDenied = results.entries
                .filter { !it.value } // Only check denied permissions
                .any { (permission, _) ->
                    activity?.shouldShowRequestPermissionRationale(permission) == false
                }
            if (anyPermanentlyDenied) {
                permanentlyDenied = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
            contentDescription = "Bluetooth",
            modifier = Modifier.size(80.dp),
            tint = if (permanentlyDenied) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (permanentlyDenied) "Permissions Blocked"
            else "Permissions Required",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (permanentlyDenied) {
                "One or more permissions have been permanently denied. " +
                    "Please open the app settings and grant Bluetooth " +
                    "and Location permissions manually to use BLE Scanner."
            } else {
                PermissionUtils.getRationaleMessage()
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (permanentlyDenied) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (permanentlyDenied) {
            // Redirect to system settings — the only way to grant
            // permissions after "Don't ask again" has been selected.
            Button(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                }
            ) {
                Text("Open Settings")
            }
        } else {
            // Standard permission request via system dialog
            Button(
                onClick = { launcher.launch(permissions.toTypedArray()) }
            ) {
                Text("Grant Permissions")
            }
        }
    }
}
