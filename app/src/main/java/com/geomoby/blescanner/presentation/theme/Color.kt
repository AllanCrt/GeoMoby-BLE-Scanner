package com.geomoby.blescanner.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Color palette for the BLE Scanner app.
 *
 * Inspired by radar and geolocation UIs: deep navy backgrounds with cyan/blue
 * accents that evoke signal visualization and tracking dashboards.
 */

// Primary palette — blue tones for primary actions and branding
val Blue80 = Color(0xFFB8C9FF)
val Blue40 = Color(0xFF3F6CFF)
val Blue20 = Color(0xFF1B3A8C)

// Secondary palette — cyan tones for secondary elements and scanning indicators
val Cyan80 = Color(0xFFB0F0F0)
val Cyan40 = Color(0xFF00BCD4)
val Cyan20 = Color(0xFF006978)

// Signal strength indicator colors (traffic light pattern: green → yellow → red)
val SignalStrong = Color(0xFF4CAF50)   // RSSI > -60 dBm
val SignalMedium = Color(0xFFFFC107)   // RSSI -60 to -80 dBm
val SignalWeak = Color(0xFFFF5722)     // RSSI < -80 dBm

// Surface colors (dark theme — inspired by IDE/dashboard aesthetics)
val SurfaceDark = Color(0xFF0D1117)            // Main background
val SurfaceCard = Color(0xFF161B22)            // Card background (generic BLE devices)
val SurfaceCardElevated = Color(0xFF1C2333)    // Elevated card (iBeacon devices)

// Text colors
val TextPrimary = Color(0xFFE6EDF3)     // High-emphasis text
val TextSecondary = Color(0xFF8B949E)   // Medium-emphasis text (MACs, labels)
val TextAccent = Color(0xFF58A6FF)      // Accent text (links, counts)

// iBeacon-specific colors — purple to distinguish from generic BLE devices
val IBeaconPurple = Color(0xFFBB86FC)
val IBeaconPurpleDim = Color(0xFF6650A4)
