package co.electriccoin.zcash.ui.common.zns

/**
 * A Zcash Name Service (ZNS) name: the bare registry name, such as `alice` for `alice.zcash`.
 *
 * Names are 1 to 63 lowercase ASCII letters or digits, so Unicode lookalikes cannot be registered.
 */
@JvmInline
value class ZnsName private constructor(
    val value: String
) {
    /** The name with the canonical `.zcash` suffix, for display. */
    val displayName: String
        get() = "$value$DISPLAY_SUFFIX"

    override fun toString(): String = displayName

    companion object {
        private const val DISPLAY_SUFFIX = ".zcash"
        private val INPUT_SUFFIXES = listOf(".zcash", ".zec")
        private val NAME_PATTERN = Regex("^[a-z0-9]{1,63}$")

        /**
         * Returns the name in recipient [input] (`Alice.ZEC` gives `alice`), or null when the input is not a ZNS name.
         *
         * Only input that ends in `.zcash` or `.zec` counts. A bare word is never looked up, so a partly typed address
         * or a contact search never reaches the name service.
         */
        fun fromRecipientInput(input: String): ZnsName? {
            val lower = input.trim().lowercase()
            val suffix = INPUT_SUFFIXES.firstOrNull { lower.endsWith(it) } ?: return null
            return fromRegistryName(lower.removeSuffix(suffix))
        }

        /** Returns [name] as the registry stores it, or null when it breaks the registry's name rules. */
        fun fromRegistryName(name: String): ZnsName? = if (NAME_PATTERN.matches(name)) ZnsName(name) else null
    }
}

/** A live registration: [name] and the [address] it points to. */
data class ZnsRegistration(
    val name: ZnsName,
    val address: String,
)
