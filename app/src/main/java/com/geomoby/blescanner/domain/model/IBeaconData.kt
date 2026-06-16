package com.geomoby.blescanner.domain.model

import java.util.UUID

/**
 * Represents parsed iBeacon advertisement data.
 *
 * iBeacon is Apple's proprietary protocol for proximity detection.
 * The UUID, major, and minor values together form a three-level hierarchy
 * that identifies a specific beacon and its location within a deployment
 * (e.g., organization → building/floor → specific zone).
 *
 * @property uuid Proximity UUID — identifies the beacon's organization or application.
 * @property major Major value — typically identifies a sub-region (e.g., a building or floor).
 * @property minor Minor value — identifies a specific beacon within the major group.
 * @property txPower Calibrated TX power at 1 meter distance (dBm), used for distance estimation.
 */
data class IBeaconData(
    val uuid: UUID,
    val major: Int,
    val minor: Int,
    val txPower: Int
)
