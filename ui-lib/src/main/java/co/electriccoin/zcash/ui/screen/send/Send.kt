package co.electriccoin.zcash.ui.screen.send

import cash.z.ecc.sdk.model.AddressType
import kotlinx.serialization.Serializable
import cash.z.ecc.android.sdk.type.AddressType as SdkAddressType

@Serializable
data class Send(
    val recipientAddress: String? = null,
    val recipientAddressType: AddressType? = null,
    val isScanZip321Enabled: Boolean = true,
    /** Zatoshi to prefill the amount with, for an entry point that already holds the whole payment. */
    val amount: Long? = null,
    val memo: String? = null
)

/** The route's type for a validated address. Shielded and Unified both open Send as UNIFIED. */
internal fun SdkAddressType.Valid.toRouteAddressType(): AddressType =
    when (this) {
        SdkAddressType.Tex -> AddressType.TEX
        SdkAddressType.Transparent -> AddressType.TRANSPARENT
        else -> AddressType.UNIFIED
    }

internal fun AddressType.toSdkAddressType(): SdkAddressType =
    when (this) {
        AddressType.UNIFIED -> SdkAddressType.Unified
        AddressType.TRANSPARENT -> SdkAddressType.Transparent
        AddressType.SAPLING -> SdkAddressType.Shielded
        AddressType.TEX -> SdkAddressType.Tex
    }
