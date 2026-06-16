package com.geomoby.blescanner.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Unit tests for [IBeaconParser.parseManufacturerData].
 *
 * Tests the pure-function iBeacon parser directly with crafted byte arrays,
 * avoiding the need to mock Android framework classes (ScanResult, ScanRecord).
 *
 * Test categories:
 * - Valid payloads: Standard iBeacon frames with known values
 * - Invalid payloads: Wrong type/length, too short, empty
 * - Edge cases: Maximum unsigned values, UUID byte order preservation
 */
class IBeaconParserTest {

    /**
     * Builds a valid iBeacon manufacturer data byte array for testing.
     *
     * This mirrors the exact format that Android's
     * [ScanRecord.getManufacturerSpecificData(0x004C)] returns:
     * type(1) + length(1) + UUID(16) + major(2) + minor(2) + txPower(1) = 23 bytes.
     */
    private fun buildIBeaconPayload(
        uuid: UUID = UUID.fromString("FDA50693-A4E2-4FB1-AFCF-C6EB07647825"),
        major: Int = 10,
        minor: Int = 5,
        txPower: Int = -59
    ): ByteArray {
        val buffer = ByteBuffer.allocate(23)
        buffer.put(0x02.toByte())          // iBeacon type
        buffer.put(0x15.toByte())          // Length (21 bytes follow)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        buffer.putShort(major.toShort())
        buffer.putShort(minor.toShort())
        buffer.put(txPower.toByte())
        return buffer.array()
    }

    // ── Valid Payload Tests ──

    @Test
    fun `valid iBeacon payload is parsed correctly`() {
        val uuid = UUID.fromString("FDA50693-A4E2-4FB1-AFCF-C6EB07647825")
        val payload = buildIBeaconPayload(uuid = uuid, major = 10, minor = 5, txPower = -59)

        val result = IBeaconParser.parseManufacturerData(payload)

        assertNotNull("Should parse a valid iBeacon payload", result)
        assertEquals(uuid, result!!.uuid)
        assertEquals(10, result.major)
        assertEquals(5, result.minor)
        assertEquals(-59, result.txPower)
    }

    @Test
    fun `parses common Kontakt iBeacon UUID correctly`() {
        val kontaktUuid = UUID.fromString("F7826DA6-4FA2-4E98-8024-BC5B71E0893E")
        val payload = buildIBeaconPayload(uuid = kontaktUuid, major = 1, minor = 100)

        val result = IBeaconParser.parseManufacturerData(payload)

        assertNotNull(result)
        assertEquals(kontaktUuid, result!!.uuid)
        assertEquals(1, result.major)
        assertEquals(100, result.minor)
    }

    // ── Invalid Payload Tests ──

    @Test
    fun `returns null for data shorter than 23 bytes`() {
        val shortPayload = ByteArray(10) { 0x00 }
        val result = IBeaconParser.parseManufacturerData(shortPayload)
        assertNull("Payloads shorter than 23 bytes should return null", result)
    }

    @Test
    fun `returns null for wrong iBeacon type byte`() {
        val payload = buildIBeaconPayload()
        payload[0] = 0x03.toByte() // Invalid type (should be 0x02)

        val result = IBeaconParser.parseManufacturerData(payload)
        assertNull("Wrong type byte should return null", result)
    }

    @Test
    fun `returns null for wrong length byte`() {
        val payload = buildIBeaconPayload()
        payload[1] = 0x10.toByte() // Wrong length (should be 0x15)

        val result = IBeaconParser.parseManufacturerData(payload)
        assertNull("Wrong length byte should return null", result)
    }

    @Test
    fun `returns null for empty byte array`() {
        val result = IBeaconParser.parseManufacturerData(ByteArray(0))
        assertNull("Empty payload should return null", result)
    }

    // ── Edge Case Tests ──

    @Test
    fun `handles maximum unsigned major and minor values (65535)`() {
        val payload = buildIBeaconPayload(major = 65535, minor = 65535)

        val result = IBeaconParser.parseManufacturerData(payload)

        assertNotNull(result)
        assertEquals(
            "Major should be 65535 (max unsigned 16-bit)",
            65535, result!!.major
        )
        assertEquals(
            "Minor should be 65535 (max unsigned 16-bit)",
            65535, result.minor
        )
    }

    @Test
    fun `handles zero major and minor values`() {
        val payload = buildIBeaconPayload(major = 0, minor = 0)

        val result = IBeaconParser.parseManufacturerData(payload)

        assertNotNull(result)
        assertEquals(0, result!!.major)
        assertEquals(0, result.minor)
    }

    @Test
    fun `preserves UUID byte order correctly`() {
        // Use a UUID where byte order matters — each hex digit is unique
        val uuid = UUID.fromString("01020304-0506-0708-090A-0B0C0D0E0F10")
        val payload = buildIBeaconPayload(uuid = uuid)

        val result = IBeaconParser.parseManufacturerData(payload)

        assertNotNull(result)
        assertEquals(
            "UUID byte order must be preserved (Big Endian)",
            uuid, result!!.uuid
        )
    }

    @Test
    fun `handles negative TX power values`() {
        val payload = buildIBeaconPayload(txPower = -65)

        val result = IBeaconParser.parseManufacturerData(payload)

        assertNotNull(result)
        assertEquals(
            "TX power should preserve signed byte value",
            -65, result!!.txPower
        )
    }

    @Test
    fun `handles positive TX power values`() {
        val payload = buildIBeaconPayload(txPower = 4)

        val result = IBeaconParser.parseManufacturerData(payload)

        assertNotNull(result)
        assertEquals(4, result!!.txPower)
    }

    @Test
    fun `payload longer than 23 bytes is still parsed correctly`() {
        // Some beacons may include extra trailing data
        val basePayload = buildIBeaconPayload(major = 42, minor = 7)
        val extendedPayload = basePayload + ByteArray(5) { 0xFF.toByte() }

        val result = IBeaconParser.parseManufacturerData(extendedPayload)

        assertNotNull("Extra trailing bytes should not break parsing", result)
        assertEquals(42, result!!.major)
        assertEquals(7, result.minor)
    }
}
