package co.electriccoin.zcash.ui.screen.send.model

import androidx.compose.runtime.saveable.mapSaver
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.common.zns.ZnsName
import co.electriccoin.zcash.ui.design.util.StringResource

/**
 * The send form's recipient. [address] is what the proposal pays and [type] its validation.
 *
 * When the user typed a ZNS name, [zns] holds the name's state and the field shows its [ZnsRecipientStatus.input].
 * [address] is then the resolved address once the name resolves, and the typed text, marked invalid, until then.
 */
data class RecipientAddressState(
    val address: String,
    val type: AddressType?,
    val zns: ZnsRecipientStatus? = null,
) {
    /** The text the recipient field shows. */
    val fieldText: String
        get() = zns?.input ?: address

    companion object {
        private const val KEY_ADDRESS = "address" // $NON-NLS
        private const val KEY_TYPE = "type" // $NON-NLS
        private const val KEY_INVALID_REASON = "invalid_reason" // $NON-NLS
        private const val TYPE_INVALID = "invalid" // $NON-NLS
        private const val TYPE_SHIELDED = "shielded" // $NON-NLS
        private const val TYPE_TRANSPARENT = "transparent" // $NON-NLS
        private const val TYPE_TEX = "tex" // $NON-NLS
        private const val TYPE_UNIFIED = "unified" // $NON-NLS

        fun new(
            address: String,
            type: AddressType? = null
        ): RecipientAddressState = RecipientAddressState(address, type)

        internal val Saver
            get() =
                run {
                    mapSaver(
                        save = { it.toSaverMap() },
                        restore = {
                            if (it.isEmpty()) {
                                null
                            } else {
                                val address = (it[KEY_ADDRESS] as String)
                                val type = (it[KEY_TYPE] as String?)
                                RecipientAddressState(
                                    address,
                                    when (type) {
                                        TYPE_INVALID -> {
                                            AddressType.Invalid(
                                                (it[KEY_INVALID_REASON] as String)
                                            )
                                        }

                                        TYPE_SHIELDED -> {
                                            AddressType.Shielded
                                        }

                                        TYPE_UNIFIED -> {
                                            AddressType.Unified
                                        }

                                        TYPE_TRANSPARENT -> {
                                            AddressType.Transparent
                                        }

                                        TYPE_TEX -> {
                                            AddressType.Tex
                                        }

                                        else -> {
                                            null
                                        }
                                    }
                                )
                            }
                        }
                    )
                }

        private fun RecipientAddressState.toSaverMap(): HashMap<String, Any> {
            val saverMap = HashMap<String, Any>()

            saverMap[KEY_ADDRESS] = this.address

            if (this.type != null) {
                saverMap[KEY_TYPE] =
                    when (this.type) {
                        is AddressType.Invalid -> {
                            saverMap[KEY_INVALID_REASON] = this.type.reason
                            TYPE_INVALID
                        }

                        AddressType.Unified -> {
                            TYPE_UNIFIED
                        }

                        AddressType.Transparent -> {
                            TYPE_TRANSPARENT
                        }

                        AddressType.Shielded -> {
                            TYPE_SHIELDED
                        }

                        AddressType.Tex -> {
                            TYPE_TEX
                        }
                    }
            }
            return saverMap
        }
    }
}

/** The state of a ZNS name typed into the recipient field. */
sealed interface ZnsRecipientStatus {
    val name: ZnsName

    /** The text the user typed, such as `Alice.zec`. */
    val input: String

    data class LookingUp(
        override val name: ZnsName,
        override val input: String,
    ) : ZnsRecipientStatus

    data class Resolved(
        override val name: ZnsName,
        override val input: String,
    ) : ZnsRecipientStatus

    data class Failed(
        override val name: ZnsName,
        override val input: String,
        val message: StringResource,
    ) : ZnsRecipientStatus
}
