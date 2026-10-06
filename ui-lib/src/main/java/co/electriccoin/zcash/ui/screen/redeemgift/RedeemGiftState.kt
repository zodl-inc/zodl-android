package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.compose.runtime.Immutable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

        val preview: RedeemGiftState = previewReady(message = "Welcome to Zcash Summit")

        val previewNoMessage: RedeemGiftState = previewReady(message = null)

        val previewChecking: RedeemGiftState = checking(onBack = {})

        val previewPending: RedeemGiftState =
            pending(
                amount = Zatoshi(PREVIEW_AMOUNT),
                isRechecking = false,
                onCheckAgain = {},
                onClose = {}
            )

        val previewEmpty: RedeemGiftState = empty(onCheckAgain = {}, onClose = {})

        val previewSuccess: RedeemGiftState = redeemed(received = Zatoshi(PREVIEW_AMOUNT), onDone = {})

        /**
         * A card that can be redeemed now. [message] is the sender's note and [walletName] the wallet the funds go to;
         * `null` picks the copy without a wallet name.
         */
        fun ready(
            redeemable: Zatoshi,
            message: String?,
            walletName: StringResource?,
            onRedeem: () -> Unit,
            onBack: () -> Unit,
        ) = Ready(
            title = stringRes(R.string.redeemGift_title),
            image = ImageResource.ByDrawable(R.drawable.ic_gift_closed),
            heading = stringRes(R.string.redeemGift_ready_title),
            amount = amount(redeemable),
            messageLabel = stringRes(R.string.redeemGift_ready_messageLabel),
            message = message?.let { stringRes(R.string.redeemGift_memo) + GIFT_CARD_MEMO_SEPARATOR + it },
            disclaimer =
                walletName
                    ?.let { stringRes(R.string.redeemGift_ready_disclaimer, it) }
                    ?: stringRes(R.string.redeemGift_ready_disclaimer_noWallet),
            redeemButton =
                ButtonState(
                    text = stringRes(R.string.redeemGift_redeem),
                    style = ButtonStyle.PRIMARY,
                    hapticFeedbackType = HapticFeedbackType.Confirm,
                    onClick = onRedeem
                ),
            onBack = onBack
        )

        fun checking(onBack: () -> Unit) =
            status(
                background = null,
                image = loadingImageRes(),
                title = stringRes(R.string.redeemGift_checking_title),
                subtitle = stringRes(R.string.redeemGift_checking_subtitle),
                primaryButton = null,
                secondaryButton = null,
                onBack = onBack,
            )

        /**
         * The card is funded, but [amount] still needs confirmations. While [isRechecking], check again shows progress.
         */
        fun pending(
            amount: Zatoshi,
            isRechecking: Boolean,
            onCheckAgain: () -> Unit,
            onClose: () -> Unit,
        ) = checkAgainStatus(
            background = TransactionProgressState.Background.PENDING,
            image = imageRes(R.drawable.ic_gift_preparing),
            title = stringRes(R.string.redeemGift_pending_title),
            subtitle = stringRes(R.string.redeemGift_pending_subtitle, stringRes(amount)),
            isRechecking = isRechecking,
            onCheckAgain = onCheckAgain,
            onClose = onClose,
        )

        fun empty(
            onCheckAgain: () -> Unit,
            onClose: () -> Unit,
        ) = checkAgainStatus(
            background = TransactionProgressState.Background.ERROR,
            image = imageRes(R.drawable.ic_gift_empty),
            title = stringRes(R.string.redeemGift_empty_title),
            subtitle = stringRes(R.string.redeemGift_empty_subtitle),
            isRechecking = false,
            onCheckAgain = onCheckAgain,
            onClose = onClose,
        )

        /**
         * The card was redeemed; [received] is `null` when the amount is not known.
         */
        fun redeemed(
            received: Zatoshi?,
            onDone: () -> Unit,
        ) = status(
            background = TransactionProgressState.Background.SUCCESS,
            image = imageRes(R.drawable.ic_gift_open),
            title = stringRes(R.string.redeemGift_success_title),
            subtitle =
                received
                    ?.let { stringRes(R.string.redeemGift_success_subtitle, stringRes(it)) }
                    ?: stringRes(R.string.redeemGift_success_subtitle_noAmount),
            primaryButton =
                ButtonState(
                    text = stringRes(R.string.general_close),
                    style = ButtonStyle.PRIMARY,
                    onClick = onDone
                ),
            secondaryButton = null,
            onBack = onDone,
            showAppBar = false,
        )

        @Suppress("LongParameterList")
        fun status(
            background: TransactionProgressState.Background?,
            image: ImageResource,
            title: StringResource,
            subtitle: StringResource,
            primaryButton: ButtonState?,
            secondaryButton: ButtonState?,
            onBack: () -> Unit,
            showAppBar: Boolean = true,
        ): RedeemGiftState =
            Status(
                TransactionProgressState(
                    background = background,
                    image = image,
                    title = title,
                    subtitle = subtitle.withStyle(),
                    middleButton = null,
                    primaryButton = primaryButton,
                    secondaryButton = secondaryButton,
                    onBack = onBack,
                    showAppBar = showAppBar,
                )
            )

        @Suppress("LongParameterList")
        private fun checkAgainStatus(
            background: TransactionProgressState.Background,
            image: ImageResource,
            title: StringResource,
            subtitle: StringResource,
            isRechecking: Boolean,
            onCheckAgain: () -> Unit,
            onClose: () -> Unit,
        ) = status(
            background = background,
            image = image,
            title = title,
            subtitle = subtitle,
            primaryButton =
                ButtonState(
                    text = stringRes(R.string.general_close),
                    style = ButtonStyle.PRIMARY,
                    onClick = onClose
                ),
            secondaryButton =
                ButtonState(
                    text = stringRes(R.string.redeemGift_checkAgain),
                    style = ButtonStyle.SECONDARY,
                    isEnabled = !isRechecking,
                    isLoading = isRechecking,
                    onClick = onCheckAgain
                ),
            onBack = onClose,
            showAppBar = false,
        )

        private fun previewReady(message: String?) =
            ready(
                redeemable = Zatoshi(PREVIEW_AMOUNT),
                message = message,
                walletName = stringRes("Zodl"),
                onRedeem = {},
                onBack = {}
            )

        private const val AMOUNT_TICKER = " ZEC"

        private const val PREVIEW_AMOUNT = 10_000_000L
    }
}
