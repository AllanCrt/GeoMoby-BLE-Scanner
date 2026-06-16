package com.geomoby.blescanner.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.geomoby.blescanner.presentation.theme.SignalMedium
import com.geomoby.blescanner.presentation.theme.SignalStrong
import com.geomoby.blescanner.presentation.theme.SignalWeak
import com.geomoby.blescanner.presentation.theme.TextAccent

/**
 * RSSI threshold filter slider composable (Bonus feature).
 *
 * Provides a Material3 slider for setting a minimum RSSI threshold.
 * Only devices with signal strength above this threshold are displayed
 * in the device list.
 *
 * ## Implementation Details
 *
 * - Range: -100 dBm (show all) to -40 dBm (only very close devices)
 * - Filtering is applied at the ViewModel level via Flow `combine` operator
 * - Devices are NOT removed from the underlying data — only filtered in the UI projection
 * - Changing the threshold instantly shows/hides devices without rescanning
 * - The slider panel animates in/out with expand/shrink transitions
 *
 * @param threshold Current threshold value in dBm.
 * @param onThresholdChange Callback when the user adjusts the slider.
 * @param isVisible Whether the slider panel is shown (toggled from app bar).
 * @param deviceCount Number of devices currently visible after filtering.
 * @param modifier Optional modifier for the composable.
 */
@Composable
fun RssiFilterSlider(
    threshold: Int,
    onThresholdChange: (Int) -> Unit,
    isVisible: Boolean,
    deviceCount: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header row: label, current value, device count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "RSSI Filter",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Current threshold value with color coding
                Text(
                    text = if (threshold <= -100) "OFF" else "$threshold dBm",
                    style = MaterialTheme.typography.labelMedium,
                    color = getThresholdColor(threshold)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Device count indicator
                Text(
                    text = "$deviceCount devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Slider control
            Slider(
                value = threshold.toFloat(),
                onValueChange = { onThresholdChange(it.toInt()) },
                valueRange = -100f..-40f,
                steps = 5, // Discrete steps at -100, -90, -80, -70, -60, -50, -40
                colors = SliderDefaults.colors(
                    thumbColor = getThresholdColor(threshold),
                    activeTrackColor = getThresholdColor(threshold)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Returns a color representing the strictness of the RSSI filter threshold.
 *
 * - **Accent blue** (≤ -100): Filter is OFF, showing all devices
 * - **Green** (≥ -60): Very strict — only very close devices
 * - **Yellow** (≥ -80): Moderate filter
 * - **Red** (< -80): Lenient filter — still showing distant devices
 */
private fun getThresholdColor(rssi: Int): Color = when {
    rssi <= -100 -> TextAccent    // Filter OFF
    rssi >= -60 -> SignalStrong   // Very close devices only
    rssi >= -80 -> SignalMedium   // Medium range
    else -> SignalWeak            // Far devices
}
