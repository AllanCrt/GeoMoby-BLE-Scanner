package com.geomoby.blescanner.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility for version-aware BLE permission handling.
 *
 * Android's BLE permission model has evolved significantly across versions:
 *
 * | Android Version | API | Required Permissions |
 * |-----------------|-----|---------------------|
 * | 6–11            | 23–30 | ACCESS_FINE_LOCATION |
 * | 12+             | 31+   | BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION* |
 * | 13+             | 33+   | + POST_NOTIFICATIONS |
 * | 14+             | 34+   | + FOREGROUND_SERVICE_CONNECTED_DEVICE (manifest only) |
 *
 * *ACCESS_FINE_LOCATION is still required on Android 12+ because we do NOT use
 * the `neverForLocation` flag on BLUETOOTH_SCAN. iBeacon UUID/major/minor are
 * inherently location-identifying data, so this is the technically correct approach
 * for a geolocation company's BLE scanner.
 */
object PermissionUtils {

    /**
     * Returns the list of runtime permissions required for BLE scanning
     * on the current Android version.
     *
     * This centralizes version checks, eliminating scattered if/else chains
     * throughout the codebase.
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31): New granular Bluetooth permissions
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        // ACCESS_FINE_LOCATION is required on ALL supported versions:
        // - Android 6–11: Required for BLE scanning API to return results.
        // - Android 12+: Required because we intentionally do NOT use the
        //   neverForLocation flag. iBeacon data is fundamentally location data.
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33): Notification permission for foreground service
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions
    }

    /**
     * Checks if all required BLE permissions are currently granted.
     *
     * @param context Application or Activity context.
     * @return true if every required permission is granted, false if any is missing.
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns a user-friendly rationale message explaining why the app needs
     * these permissions.
     *
     * This message is shown before the system permission dialog when the user
     * has previously denied a permission, as recommended by Android guidelines.
     */
    fun getRationaleMessage(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "BLE Scanner needs Bluetooth and location permissions to discover " +
                "nearby beacons. Notification permission is needed to keep " +
                "scanning active in the background."
        } else {
            "BLE Scanner needs location permission to discover nearby Bluetooth " +
                "beacons. On this Android version, location access is required " +
                "by the system for BLE scanning."
        }
    }
}
