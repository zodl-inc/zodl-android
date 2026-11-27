package co.electriccoin.zcash.ui.common.model

/**
 * Keystone hardware-wallet firmware version triple exactly as keystone3-firmware stamps it into
 * every signed PCZT's `global.proprietary["keystone:fw_version"]` field. This is the device's
 * **raw internal** numbering, not what the device screen displays — `major` carries a
 * +[KeystoneFirmwareVersion.STAMPED_MAJOR_OFFSET] offset over the displayed major version.
 * Never compare this directly against [KeystoneFirmwareVersion.MINIMUM_SUPPORTED]; convert it
 * with [KeystoneFirmwareVersion.fromStamp] first.
 */
data class KeystoneFirmwareStamp(
    val major: Int,
    val minor: Int,
    val build: Int
) {
    override fun toString() = "$major.$minor.$build"
}

/**
 * Keystone hardware-wallet firmware version in **display** numbering — the triple the device
 * screen shows the user, and the only numbering [MINIMUM_SUPPORTED] and comparisons should use.
 * Construct this from a raw [KeystoneFirmwareStamp] via [fromStamp] for the legacy single-
 * transaction PCZT-echo path, or from [toKeystoneFwVersion] for the batch-sign migration path;
 * the `displayMajor` label on the constructor exists to make every call site say which numbering
 * it's using.
 */
data class KeystoneFirmwareVersion(
    val displayMajor: Int,
    val minor: Int,
    val build: Int
) : Comparable<KeystoneFirmwareVersion> {
    override fun compareTo(other: KeystoneFirmwareVersion): Int =
        compareValuesBy(this, other, { it.displayMajor }, { it.minor }, { it.build })

    override fun toString() = "$displayMajor.$minor.$build"

    companion object {
        /**
         * Keystone's `version.h` stamps the device's raw internal major version into signed
         * PCZTs; keystone3-firmware renders `MAJOR - STAMPED_MAJOR_OFFSET` on the device screen.
         * This offset has held at every tag from 2.2.8 through 3.0.0 — a contract, not an
         * oversight — and was confirmed against a physical device.
         */
        const val STAMPED_MAJOR_OFFSET = 10

        /**
         * Minimum Keystone firmware this app will accept a signature from — set by product
         * (MOB-1510; initially 3.0.3, lowered to 3.0.1). Expressed in display numbering — the
         * version the device screen (and the error prompt) show. Single point of change if the
         * minimum ever changes. Always enforced — there is no "disable the check" escape hatch.
         */
        val MINIMUM_SUPPORTED = KeystoneFirmwareVersion(displayMajor = 3, minor = 0, build = 1)

        /**
         * Converts a raw [KeystoneFirmwareStamp] into display numbering by removing
         * [STAMPED_MAJOR_OFFSET] from its major component. Firmware below the offset threshold
         * is taken as already normalized, so this degrades to the identity transform if Keystone
         * ever stamps display numbering directly.
         */
        fun fromStamp(stamp: KeystoneFirmwareStamp): KeystoneFirmwareVersion =
            KeystoneFirmwareVersion(
                displayMajor =
                    if (stamp.major >= STAMPED_MAJOR_OFFSET) {
                        stamp.major - STAMPED_MAJOR_OFFSET
                    } else {
                        stamp.major
                    },
                minor = stamp.minor,
                build = stamp.build
            )
    }
}

private val FIRMWARE_VERSION_KEY = "keystone:fw_version".toByteArray(Charsets.US_ASCII)
private const val FIRMWARE_VERSION_VALUE_LENGTH = 3

/**
 * Scans a signed PCZT's raw bytes for the Keystone firmware version stamp.
 *
 * PCZT proprietary fields are postcard-encoded `BTreeMap<String, Vec<u8>>` entries: a varint key
 * length, the UTF-8 key bytes, a varint value length, then the value bytes. For the 3-byte
 * firmware version value the length byte is always `0x03`, so this looks for the ASCII key
 * literal directly in the byte stream and reads the 3 bytes immediately following the expected
 * `0x03` length byte. Returns `null` if the key isn't present (legacy firmware that predates the
 * stamping feature) or the bytes that follow don't match the expected shape.
 *
 * The bytes are returned exactly as the device wrote them — raw internal numbering, not display
 * numbering. Use [KeystoneFirmwareVersion.fromStamp] to convert before comparing against
 * [KeystoneFirmwareVersion.MINIMUM_SUPPORTED].
 */
fun ByteArray.readKeystoneFwStamp(): KeystoneFirmwareStamp? {
    val keyStart = indexOfSubArray(FIRMWARE_VERSION_KEY)
    if (keyStart < 0) return null

    val lengthIndex = keyStart + FIRMWARE_VERSION_KEY.size
    val valueStart = lengthIndex + 1
    val hasValidStamp =
        lengthIndex < size &&
            this[lengthIndex] == FIRMWARE_VERSION_VALUE_LENGTH.toByte() &&
            valueStart + FIRMWARE_VERSION_VALUE_LENGTH <= size

    return if (hasValidStamp) {
        KeystoneFirmwareStamp(
            major = this[valueStart].toInt() and 0xFF,
            minor = this[valueStart + 1].toInt() and 0xFF,
            build = this[valueStart + 2].toInt() and 0xFF,
        )
    } else {
        null
    }
}

private fun ByteArray.indexOfSubArray(needle: ByteArray): Int {
    if (needle.isEmpty() || needle.size > size) return -1
    return (0..(size - needle.size)).firstOrNull { i ->
        needle.indices.all { j -> this[i + j] == needle[j] }
    } ?: -1
}

/**
 * Keystone hardware-wallet firmware version, as reported by the device itself in the
 * `zcash-batch-sig-result` UR envelope's dedicated firmware-version field (CBOR key 3) — see
 * `ZcashBatchSigResult::get_firmware_version()` in keystone-sdk-rust's `ur-registry` crate.
 *
 * Converts the raw `[major, minor, build]` bytes carried directly in the batch-sign-result UR
 * envelope (`KeystoneBatchDecodeResult.firmwareVersion`) into a [KeystoneFirmwareVersion].
 *
 * Unlike the legacy single-transaction PCZT-echo response ([readKeystoneFwStamp]), the compact
 * batch protocol never echoes signed PCZT bytes back (`BatchSignResponse` is signatures-only), so
 * there is no `keystone:fw_version` proprietary field to scan for on that path — the envelope's
 * own dedicated field is the only source of the firmware version for migration.
 *
 * ASSUMPTION (unverified against a physical device with old + new firmware): this field reports
 * DISPLAY numbering directly, not the legacy PCZT-stamp's raw/offset numbering — so no
 * [KeystoneFirmwareVersion.fromStamp] conversion is applied here, matching prior behavior.
 * If migration firmware gating is ever found to under- or over-block, verify this first.
 *
 * Returns `null` if the byte array isn't exactly 3 bytes (device didn't report a version —
 * pre-migration-support firmware).
 */
fun ByteArray.toKeystoneFwVersion(): KeystoneFirmwareVersion? {
    if (size != FIRMWARE_VERSION_VALUE_LENGTH) return null
    return KeystoneFirmwareVersion(
        displayMajor = this[0].toInt() and 0xFF,
        minor = this[1].toInt() and 0xFF,
        build = this[2].toInt() and 0xFF,
    )
}

/**
 * Decides whether a Keystone-signed transaction may proceed to broadcast, given the firmware
 * version (if any) detected on the signed PCZT or batch-sign-result envelope, against whatever
 * minimum the caller requires.
 */
object KeystoneFirmwarePolicy {
    enum class Outcome {
        /** Firmware reported a version and it meets [required]. */
        OK,

        /** Firmware reported a version but it's below [required]. */
        UPDATE_REQUIRED,

        /** Firmware didn't report a version at all (pre-stamp build). */
        LEGACY,
    }

    fun evaluate(
        detected: KeystoneFirmwareVersion?,
        required: KeystoneFirmwareVersion
    ): Outcome {
        if (detected == null) return Outcome.LEGACY
        return if (detected >= required) Outcome.OK else Outcome.UPDATE_REQUIRED
    }
}
