package co.electriccoin.zcash.ui.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeystoneFirmwareVersionTest {
    @Test
    fun minimumSupportedMatchesProductRequirement() {
        assertEquals(
            KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 1),
            KeystoneFirmwareVersion.MINIMUM_SUPPORTED
        )
    }

    @Test
    fun toStringRendersDottedTriple() {
        assertEquals("3.0.3", KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 3).toString())
    }

    @Test
    fun minimumSupportedIsNotBelowItself() {
        assertFalse(KeystoneFirmwareVersion.MINIMUM_SUPPORTED < KeystoneFirmwareVersion.MINIMUM_SUPPORTED)
    }

    @Test
    fun comparableOrdersByMajorThenMinorThenBuild() {
        assertTrue(
            KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 2) >
                KeystoneFirmwareVersion(displayMajor = 2, minor = 9, build = 9)
        )
        assertTrue(
            KeystoneFirmwareVersion(displayMajor = 3, minor = 1, build = 0) >
                KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 2)
        )
        assertTrue(
            KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 3) >
                KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 2)
        )
        assertEquals(
            KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 2),
            KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 2)
        )
    }

    @Test
    fun readsStampWhenPresent() {
        val bytes = pcztBytesWithStamp(major = 13, minor = 0, build = 1)

        assertEquals(KeystoneFirmwareStamp(major = 13, minor = 0, build = 1), bytes.readKeystoneFwStamp())
    }

    @Test
    fun readsStampWhenNotAtStartOfArray() {
        val bytes = byteArrayOf(0x0A, 0x0B, 0x0C) + pcztBytesWithStamp(major = 12, minor = 4, build = 6)

        assertEquals(KeystoneFirmwareStamp(major = 12, minor = 4, build = 6), bytes.readKeystoneFwStamp())
    }

    @Test
    fun returnsNullWhenKeyAbsent() {
        val bytes = "no proprietary fields here".toByteArray(Charsets.US_ASCII)

        assertNull(bytes.readKeystoneFwStamp())
    }

    @Test
    fun returnsNullWhenLengthByteIsNotThree() {
        val key = "keystone:fw_version".toByteArray(Charsets.US_ASCII)
        val bytes = key + byteArrayOf(0x04, 1, 2, 3, 4)

        assertNull(bytes.readKeystoneFwStamp())
    }

    @Test
    fun returnsNullWhenArrayTruncatedRightAfterKey() {
        val key = "keystone:fw_version".toByteArray(Charsets.US_ASCII)
        val bytes = key + byteArrayOf(0x03, 1)

        assertNull(bytes.readKeystoneFwStamp())
    }

    @Test
    fun returnsNullOnEmptyArray() {
        assertNull(ByteArray(0).readKeystoneFwStamp())
    }

    @Test
    fun fromStampNormalizesTheDefectCase() {
        val stamp = KeystoneFirmwareStamp(major = 13, minor = 0, build = 1)

        assertEquals(
            KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 1),
            KeystoneFirmwareVersion.fromStamp(stamp)
        )
    }

    @Test
    fun fromStampNormalizesAnotherStampedMajor() {
        val stamp = KeystoneFirmwareStamp(major = 12, minor = 4, build = 6)

        assertEquals(
            KeystoneFirmwareVersion(displayMajor = 2, minor = 4, build = 6),
            KeystoneFirmwareVersion.fromStamp(stamp)
        )
    }

    @Test
    fun fromStampInvertedComparisonAgainstMinimum() {
        val stamp = KeystoneFirmwareStamp(major = 13, minor = 0, build = 1)

        assertTrue(
            KeystoneFirmwareVersion.fromStamp(stamp) < KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 3)
        )
    }

    @Test
    fun fromStampPassesThroughBelowOffsetMajor() {
        val stamp = KeystoneFirmwareStamp(major = 3, minor = 0, build = 1)

        assertEquals(
            KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 1),
            KeystoneFirmwareVersion.fromStamp(stamp)
        )
    }

    @Test
    fun fromStampBoundaryAtOffsetIsNormalizedToZero() {
        val stamp = KeystoneFirmwareStamp(major = 10, minor = 0, build = 0)

        assertEquals(
            KeystoneFirmwareVersion(displayMajor = 0, minor = 0, build = 0),
            KeystoneFirmwareVersion.fromStamp(stamp)
        )
    }

    @Test
    fun fromStampBoundaryJustBelowOffsetPassesThrough() {
        val stamp = KeystoneFirmwareStamp(major = 9, minor = 9, build = 9)

        assertEquals(
            KeystoneFirmwareVersion(displayMajor = 9, minor = 9, build = 9),
            KeystoneFirmwareVersion.fromStamp(stamp)
        )
    }

    @Test
    fun fromStampNeverOffsetsMinorOrBuild() {
        val stamp = KeystoneFirmwareStamp(major = 13, minor = 12, build = 11)

        assertEquals(
            KeystoneFirmwareVersion(displayMajor = 3, minor = 12, build = 11),
            KeystoneFirmwareVersion.fromStamp(stamp)
        )
    }

    // ── toKeystoneFwVersion (migration batch-envelope's own field, not the legacy PCZT stamp) ──

    @Test
    fun convertsThreeByteArray() {
        val bytes = byteArrayOf(3, 0, 2)

        assertEquals(KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 2), bytes.toKeystoneFwVersion())
    }

    @Test
    fun convertsUnsignedByteValues() {
        val bytes = byteArrayOf(12, 4, 0xFF.toByte())

        assertEquals(KeystoneFirmwareVersion(displayMajor = 12, minor = 4, build = 255), bytes.toKeystoneFwVersion())
    }

    @Test
    fun returnsNullWhenNotThreeBytes() {
        assertNull(ByteArray(0).toKeystoneFwVersion())
        assertNull(byteArrayOf(1, 2).toKeystoneFwVersion())
        assertNull(byteArrayOf(1, 2, 3, 4).toKeystoneFwVersion())
    }

    @Test
    fun evaluateReturnsOkWhenDetectedMeetsRequired() {
        val required = KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 2)

        assertEquals(
            KeystoneFirmwarePolicy.Outcome.OK,
            KeystoneFirmwarePolicy.evaluate(
                detected = KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 2),
                required = required
            )
        )
        assertEquals(
            KeystoneFirmwarePolicy.Outcome.OK,
            KeystoneFirmwarePolicy.evaluate(
                detected = KeystoneFirmwareVersion(displayMajor = 3, minor = 1, build = 0),
                required = required
            )
        )
    }

    @Test
    fun evaluateReturnsUpdateRequiredWhenDetectedBelowRequired() {
        val required = KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 2)

        assertEquals(
            KeystoneFirmwarePolicy.Outcome.UPDATE_REQUIRED,
            KeystoneFirmwarePolicy.evaluate(
                detected = KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 1),
                required = required
            )
        )
        assertEquals(
            KeystoneFirmwarePolicy.Outcome.UPDATE_REQUIRED,
            KeystoneFirmwarePolicy.evaluate(
                detected = KeystoneFirmwareVersion(displayMajor = 2, minor = 9, build = 9),
                required = required
            )
        )
    }

    @Test
    fun evaluateReturnsLegacyWhenDetectedIsNullRegardlessOfRequired() {
        assertEquals(
            KeystoneFirmwarePolicy.Outcome.LEGACY,
            KeystoneFirmwarePolicy.evaluate(
                detected = null,
                required = KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 2)
            )
        )
        assertEquals(
            KeystoneFirmwarePolicy.Outcome.LEGACY,
            KeystoneFirmwarePolicy.evaluate(
                detected = null,
                required = KeystoneFirmwareVersion(displayMajor = 0, minor = 0, build = 0)
            )
        )
    }

    private fun pcztBytesWithStamp(
        major: Int,
        minor: Int,
        build: Int
    ): ByteArray {
        val key = "keystone:fw_version".toByteArray(Charsets.US_ASCII)
        return byteArrayOf(0x01, 0x02) + key + byteArrayOf(0x03, major.toByte(), minor.toByte(), build.toByte()) +
            byteArrayOf(0x09, 0x08)
    }
}
