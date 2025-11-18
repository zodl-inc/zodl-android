package co.electriccoin.zcash.ui.screen.send

import cash.z.ecc.sdk.model.AddressType
import kotlinx.serialization.Serializable

@Serializable
data class Send(
    val recipientAddress: String? = null,
    val recipientAddressType: AddressType? = null,
    val isScanZip321Enabled: Boolean = true,
    /**
     * Zatoshi to prefill the amount field with, for an entry point that already knows the whole
     * payment - a shared ZIP-321 URI. Carried on the route rather than published through
     * [co.electriccoin.zcash.ui.common.usecase.PrefillSendUseCase] so that it survives the screen
     * being remounted: that bus is a rendezvous channel, so a value published while another Send is
     * still collecting is consumed by the screen on its way out.
     */
    val amount: Long? = null,
    val memo: String? = null
)
