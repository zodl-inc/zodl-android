package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.compose.runtime.Immutable
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.GIFT_CARD_MEMO_SEPARATOR
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.StyledStringResource
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.TickerLocation
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
     * @param amount what the user receives: the card's spendable funds minus the network fee, with a muted ticker.
     * @param message the note written into the link by the card's sender, shown as the redeem memo will read, under
     * [messageLabel]; `null` when the card has none, which hides both.
     * @param disclaimer where the funds go and that [amount] is already net of the network fee.
     */
    @Immutable
    data class Ready(
        val title: StringResource,
        val image: ImageResource.ByDrawable,
        val heading: StringResource,
        val amount: StyledStringResource,
        val messageLabel: StringResource,
        val message: StringResource?,
        val disclaimer: StringResource,
        val redeemButton: ButtonState,
        override val onBack: () -> Unit,
    ) : RedeemGiftState

    companion object {
        /**
         * The amount of a [Ready] card: [value] without a ticker, followed by a muted " ZEC".
         */
        fun amount(value: Zatoshi): StyledStringResource =
            stringRes(value, TickerLocation.HIDDEN).withStyle() +
                stringRes(AMOUNT_TICKER).withStyle(StyledStringStyle(color = StringResourceColor.QUARTERNARY))

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
                    showAppBar = true
                )
            )

        val previewPending: RedeemGiftState =
            Status(
                TransactionProgressState(
                    background = TransactionProgressState.Background.PENDING,
                    image = imageRes(R.drawable.ic_gift_preparing),
                    title = stringRes(R.string.redeemGift_pending_title),
                    subtitle =
                        stringRes(R.string.redeemGift_pending_subtitle, stringRes(Zatoshi(PREVIEW_AMOUNT)))
                            .withStyle(),
                    middleButton = null,
                    primaryButton =
                        ButtonState(text = stringRes(R.string.general_close), style = ButtonStyle.PRIMARY),
                    secondaryButton =
                        ButtonState(text = stringRes(R.string.redeemGift_checkAgain), style = ButtonStyle.SECONDARY),
                    onBack = {},
                    showAppBar = false
                )
            )

        val previewSuccess: RedeemGiftState =
            Status(
                TransactionProgressState(
                    background = TransactionProgressState.Background.SUCCESS,
                    image = imageRes(R.drawable.ic_gift_open),
                    title = stringRes(R.string.redeemGift_success_title),
                    subtitle =
                        stringRes(R.string.redeemGift_success_subtitle, stringRes(Zatoshi(PREVIEW_AMOUNT)))
                            .withStyle(),
                    middleButton = null,
                    primaryButton =
                        ButtonState(text = stringRes(R.string.general_close), style = ButtonStyle.PRIMARY),
                    secondaryButton = null,
                    onBack = {},
                    showAppBar = false
                )
            )

        private fun previewReady(message: StringResource?) =
            Ready(
                title = stringRes(R.string.redeemGift_title),
                image = ImageResource.ByDrawable(R.drawable.ic_gift_closed),
                heading = stringRes(R.string.redeemGift_ready_title),
                amount = amount(Zatoshi(PREVIEW_AMOUNT)),
                messageLabel = stringRes(R.string.redeemGift_ready_messageLabel),
                message = message?.let { stringRes(R.string.redeemGift_memo) + GIFT_CARD_MEMO_SEPARATOR + it },
                disclaimer = stringRes(R.string.redeemGift_ready_disclaimer, stringRes("Zodl")),
                redeemButton = ButtonState(text = stringRes(R.string.redeemGift_redeem), style = ButtonStyle.PRIMARY),
                onBack = {}
            )

        private const val AMOUNT_TICKER = " ZEC"

        private const val PREVIEW_AMOUNT = 10_000_000L
    }
}
