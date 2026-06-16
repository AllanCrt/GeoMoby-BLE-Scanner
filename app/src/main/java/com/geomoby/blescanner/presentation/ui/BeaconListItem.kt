package com.geomoby.blescanner.presentation.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geomoby.blescanner.domain.model.BleDevice
import com.geomoby.blescanner.presentation.theme.IBeaconPurple
import com.geomoby.blescanner.presentation.theme.IBeaconPurpleDim
import com.geomoby.blescanner.presentation.theme.SignalMedium
import com.geomoby.blescanner.presentation.theme.SignalStrong
import com.geomoby.blescanner.presentation.theme.SignalWeak
import com.geomoby.blescanner.presentation.theme.SurfaceCard
import com.geomoby.blescanner.presentation.theme.SurfaceCardElevated
import com.geomoby.blescanner.presentation.theme.TextSecondary

/**
 * Single list item composable for a discovered BLE device.
 *
 * Displays:
 * - Signal strength indicator (colored dot: green/yellow/red)
 * - Device icon (location pin for iBeacons, Bluetooth icon for generic devices)
 * - Device name (or "Unknown Device" if not broadcasting a name)
 * - MAC address in monospace font
 * - Animated RSSI value with color transition
 * - iBeacon data section (UUID, major, minor) for iBeacon devices
 *
 * iBeacon devices are visually distinguished with:
 * - Slightly elevated card background
 * - Purple accent tag
 * - Location pin icon instead of Bluetooth icon
 *
 * @param device The BLE device to display.
 * @param modifier Optional modifier for the composable.
 */
@Composable
fun BeaconListItem(
    device: BleDevice,
    modifier: Modifier = Modifier
) {
    val isIBeacon = device.iBeaconData != null

    // Animate RSSI value changes for a smooth visual experience
    val animatedRssi by animateIntAsState(
        targetValue = device.rssi,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rssi_animation"
    )

    // Animate the RSSI color to match signal strength transitions
    val rssiColor by animateColorAsState(
        targetValue = getRssiColor(device.rssi),
        label = "rssi_color_animation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            // iBeacon cards use a slightly elevated background for visual distinction
            containerColor = if (isIBeacon) SurfaceCardElevated else SurfaceCard
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // ── Top row: Device info + RSSI badge ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: signal dot + icon + name/MAC
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Signal strength indicator dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(rssiColor)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Device type icon
                    Icon(
                        imageVector = if (isIBeacon) Icons.Default.LocationOn
                        else Icons.Default.Bluetooth,
                        contentDescription = if (isIBeacon) "iBeacon" else "BLE Device",
                        tint = if (isIBeacon) IBeaconPurple
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Device name and MAC address
                    Column {
                        Text(
                            text = device.name ?: "Unknown Device",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = device.macAddress,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }

                // Right side: RSSI value badge
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "$animatedRssi",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = rssiColor
                    )
                    Text(
                        text = "dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // ── iBeacon data section (only for iBeacon devices) ──
            if (isIBeacon) {
                val iBeacon = device.iBeaconData!!

                Spacer(modifier = Modifier.height(12.dp))

                // Subtle purple divider line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(IBeaconPurpleDim.copy(alpha = 0.3f))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // iBeacon tag + major/minor values
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "iBeacon" badge chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(IBeaconPurpleDim.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "iBeacon",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = IBeaconPurple
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Major: ${iBeacon.major}  •  Minor: ${iBeacon.minor}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Proximity UUID in monospace for readability
                Text(
                    text = "UUID: ${iBeacon.uuid}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Maps an RSSI value to a traffic-light signal strength color.
 *
 * - **Green** (> -60 dBm): Strong signal, device is very close
 * - **Yellow** (-60 to -80 dBm): Medium signal, moderate distance
 * - **Red** (< -80 dBm): Weak signal, device is far or obstructed
 */
private fun getRssiColor(rssi: Int): Color = when {
    rssi >= -60 -> SignalStrong
    rssi >= -80 -> SignalMedium
    else -> SignalWeak
}
