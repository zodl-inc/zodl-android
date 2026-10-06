package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.compose.runtime.Immutable
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.loadingImageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState

@Immutable
sealed interface RedeemGiftState {
    val onBack: () -> Unit

    /**
     * Checking, pending, empty, error, redeeming and result screens, all drawn with the transaction progress layout.
     */
    @Immutable
    data class Status(
        val progress: TransactionProgressState
    ) : RedeemGiftState {
        override val onBack: () -> Unit
            get() = progress.onBack
    }

    /**
     * The card holds funds that can be redeemed now.
     *
     * @param amount what the user receives: the card's spendable funds minus the network fee.
     * @param fiatAmount [amount] in the user's currency; `null` without an exchange rate.
     * @param feeHint says that [amount] is net of the network fee, which the card pays.
     * @param message the note written into the link by the card's sender, if any, shown under [messageLabel].
     * @param destination where the funds go; `null` while the selected wallet is not known yet.
     */
    @Immutable
    data class Ready(
        val title: StringResource,
        val image: ImageResource.ByDrawable,
        val heading: StringResource,
        val amount: StringResource,
        val fiatAmount: StringResource?,
        val feeHint: StringResource,
        val messageLabel: StringResource,
        val message: StringResource?,
        val destination: StringResource?,
        val redeemButton: ButtonState,
        override val onBack: () -> Unit,
    ) : RedeemGiftState

    companion object {
        val preview: RedeemGiftState = previewReady(message = stringRes("Welcome to Zcash Summit"))

        val previewNoMessage: RedeemGiftState = previewReady(message = null)

        val previewChecking: RedeemGiftState =
            Status(
                TransactionProgressState(
                    background = null,
                    image = loadingImageRes(),
                    title = stringRes(R.string.redeemGift_checking_title),
                    subtitle = stringRes(R.string.redeemGift_checking_subtitle).withStyle(),
                    middleButton = null,
                    primaryButton = null,
                    secondaryButton = null,
                    onBack = {},
                    showAppBar = true,
                    centerContent = true
                )
            )

        val previewPending: RedeemGiftState =
            Status(
                TransactionProgressState(
                    background = TransactionProgressState.Background.PENDING,
                    image = imageRes(R.drawable.ic_face_star),
                    title = stringRes(R.string.redeemGift_pending_title),
                    subtitle =
                        stringRes(R.string.redeemGift_pending_subtitle, stringRes(Zatoshi(PREVIEW_AMOUNT)))
                            .withStyle(),
                    middleButton = null,
                    primaryButton =
                        ButtonState(text = stringRes(R.string.redeemGift_checkAgain), style = ButtonStyle.PRIMARY),
                    secondaryButton =
                        ButtonState(text = stringRes(R.string.general_close), style = ButtonStyle.SECONDARY),
                    onBack = {},
                    showAppBar = true,
                    centerContent = true
                )
            )

        private fun previewReady(message: StringResource?) =
            Ready(
                title = stringRes(R.string.redeemGift_title),
                image = ImageResource.ByDrawable(R.drawable.ic_integrations_gift),
                heading = stringRes(R.string.redeemGift_ready_title),
                amount = stringRes(Zatoshi(PREVIEW_AMOUNT)),
                fiatAmount = stringRes("$4.12"),
                feeHint = stringRes(R.string.redeemGift_ready_feeHint),
                messageLabel = stringRes(R.string.redeemGift_ready_messageLabel),
                message = message,
                destination = stringRes(R.string.redeemGift_ready_destination, stringRes("Zodl")),
                redeemButton = ButtonState(text = stringRes(R.string.redeemGift_redeem), style = ButtonStyle.PRIMARY),
                onBack = {}
            )

        private const val PREVIEW_AMOUNT = 10_000_000L
    }
}
