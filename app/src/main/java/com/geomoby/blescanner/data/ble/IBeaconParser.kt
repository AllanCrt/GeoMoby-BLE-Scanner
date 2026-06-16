package com.geomoby.blescanner.data.ble

import android.bluetooth.le.ScanResult
import com.geomoby.blescanner.domain.model.IBeaconData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Parser for iBeacon advertisement frames from raw BLE scan results.
 *
 * iBeacon is Apple's proprietary proximity beacon protocol. The beacon data
 * is embedded within the Manufacturer Specific Data field of the BLE advertisement,
 * using Apple's Bluetooth SIG Company ID (0x004C).
 *
 * ## iBeacon Frame Structure
 *
 * After stripping the Company ID (handled by Android's [ScanRecord.getManufacturerSpecificData]),
 * the remaining manufacturer data has this layout:
 *
 * ```
 * ┌──────────┬──────────┬────────────────────┬───────┬───────┬──────────┐
 * │ Byte 0   │ Byte 1   │ Bytes 2–17         │ 18–19 │ 20–21 │ Byte 22  │
 * │ Type=0x02│ Len=0x15 │ Proximity UUID     │ Major │ Minor │ TX Power │
 * └──────────┴──────────┴────────────────────┴───────┴───────┴──────────┘
 * ```
 *
 * Total payload: 23 bytes.
 *
 * ## Design Decision
 *
 * This parser is implemented as a stateless singleton with pure functions,
 * making it fully unit-testable without mocking Android framework classes.
 * The internal [parseManufacturerData] method accepts raw byte arrays,
 * enabling direct testing with crafted payloads.
 */
object IBeaconParser {

    /** Apple's Bluetooth SIG Company Identifier. */
    private const val APPLE_COMPANY_ID = 0x004C

    /** iBeacon advertisement subtype identifier. */
    private const val IBEACON_TYPE = 0x02

    /** Expected length field value (21 bytes of iBeacon data follow the type+length header). */
    private const val IBEACON_DATA_LENGTH = 0x15

    /** Minimum required byte count in the manufacturer data payload. */
    private const val MIN_PAYLOAD_SIZE = 23

    /**
     * Attempts to parse iBeacon data from a BLE [ScanResult].
     *
     * @param scanResult The raw BLE scan result from [android.bluetooth.le.ScanCallback].
     * @return Parsed [IBeaconData] if the device is broadcasting a valid iBeacon frame,
     *         null otherwise.
     */
    fun parse(scanResult: ScanResult): IBeaconData? {
        val scanRecord = scanResult.scanRecord ?: return null

        // getManufacturerSpecificData returns the data bytes AFTER the Company ID,
        // so we get the iBeacon payload directly (type + length + UUID + major + minor + txPower).
        val manufacturerData = scanRecord.getManufacturerSpecificData(APPLE_COMPANY_ID)
            ?: return null

        return parseManufacturerData(manufacturerData)
    }

    /**
     * Parses iBeacon fields from raw Apple manufacturer-specific data bytes.
     *
     * This method is separated from [parse] to enable direct unit testing
     * with crafted byte arrays, without needing to mock a full [ScanResult].
     *
     * @param data Raw manufacturer data bytes (excluding the 2-byte Company ID prefix,
     *             which is already stripped by [ScanRecord.getManufacturerSpecificData]).
     * @return Parsed [IBeaconData] or null if the data doesn't match the iBeacon format.
     */
    internal fun parseManufacturerData(data: ByteArray): IBeaconData? {
        if (data.size < MIN_PAYLOAD_SIZE) return null

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // Validate iBeacon type and length markers.
        // Type must be 0x02 (iBeacon) and length must be 0x15 (21 bytes following).
        val type = buffer.get().toInt() and 0xFF
        val length = buffer.get().toInt() and 0xFF
        if (type != IBEACON_TYPE || length != IBEACON_DATA_LENGTH) return null

        // Parse Proximity UUID (128-bit = two 64-bit longs in Big Endian order).
        val uuidMostSigBits = buffer.long
        val uuidLeastSigBits = buffer.long
        val uuid = UUID(uuidMostSigBits, uuidLeastSigBits)

        // Parse Major and Minor as unsigned 16-bit integers.
        // Kotlin's Short is signed, so we mask with 0xFFFF to get unsigned values.
        val major = buffer.short.toInt() and 0xFFFF
        val minor = buffer.short.toInt() and 0xFFFF

        // Parse TX Power as a signed 8-bit integer (represents calibrated RSSI at 1 meter).
        val txPower = buffer.get().toInt()

        return IBeaconData(
            uuid = uuid,
            major = major,
            minor = minor,
            txPower = txPower
        )
    }
}
