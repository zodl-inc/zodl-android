package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.GiftCardFailure
import co.electriccoin.zcash.ui.common.model.GiftCardPhase
import co.electriccoin.zcash.ui.common.model.GiftCardSession
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.GiftCardRepository
import co.electriccoin.zcash.ui.common.usecase.GetGiftCardDestinationAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.loadingImageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState.Background.ERROR
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The gift card redeem screen. The redemption itself is a session of [GiftCardRepository], which runs it
 * independently of this screen: this view model only shows the session and forwards the user's intents. Leaving the
 * screen with back or close ends the session (except while the card is being redeemed, when back does nothing);
 * the screen merely going away does not.
 */
class RedeemGiftVM(
    private val args: RedeemGiftArgs,
    private val giftCardRepository: GiftCardRepository,
    private val getDestinationAddress: GetGiftCardDestinationAddressUseCase,
    private val navigationRouter: NavigationRouter,
    getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
) : ViewModel() {
    val state: StateFlow<RedeemGiftState?> =
        combine(
            giftCardRepository.observeSession(args.linkId),
            getSelectedWalletAccount.observe(),
        ) { session, account ->
            createState(session, account)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun onRedeemClick() = giftCardRepository.redeem(args.linkId) { getDestinationAddress() }

    private fun onRetryClick() = giftCardRepository.checkAgain(args.linkId)

    private fun onBack() {
        giftCardRepository.dismiss(args.linkId)
        navigationRouter.back()
    }

    private fun onDoneClick() {
        giftCardRepository.dismiss(args.linkId)
        navigationRouter.backToRoot()
    }

    private fun createState(
        session: GiftCardSession,
        account: WalletAccount?
    ): RedeemGiftState =
        when (val phase = session.phase) {
            GiftCardPhase.Checking -> {
                RedeemGiftState.checking(onBack = ::onBack)
            }

            is GiftCardPhase.Ready -> {
                RedeemGiftState.ready(
                    redeemable = phase.redeemable,
                    ticker = stringRes(CURRENCY_TICKER),
                    message = session.summary?.message?.takeIf { it.isNotBlank() },
                    walletName = account?.name,
                    onRedeem = ::onRedeemClick,
                    onBack = ::onBack
                )
            }

            is GiftCardPhase.Pending -> {
                RedeemGiftState.pending(
                    amount = phase.pending,
                    isRechecking = phase.isRechecking,
                    onCheckAgain = ::onRetryClick,
                    onClose = ::onBack
                )
            }

            is GiftCardPhase.Empty -> {
                RedeemGiftState.empty(
                    isDust = phase.isDust,
                    isRechecking = phase.isRechecking,
                    onCheckAgain = ::onRetryClick,
                    onClose = ::onBack
                )
            }

            GiftCardPhase.Redeeming -> {
                RedeemGiftState.status(
                    background = null,
                    image = loadingImageRes(),
                    title = stringRes(R.string.redeemGift_redeeming_title),
                    subtitle = stringRes(R.string.redeemGift_redeeming_subtitle),
                    primaryButton = null,
                    secondaryButton = null,
                    onBack = {},
                    showAppBar = false,
                    centerContent = true,
                )
            }

            is GiftCardPhase.Redeemed -> {
                RedeemGiftState.redeemed(received = phase.redemption.received, onDone = ::onDoneClick)
            }

            is GiftCardPhase.Failed -> {
                failureStatus(phase.failure)
            }
        }

    private fun failureStatus(failure: GiftCardFailure): RedeemGiftState =
        when (failure) {
            GiftCardFailure.INVALID_LINK -> {
                errorStatus(R.string.redeemGift_invalid_title, R.string.redeemGift_invalid_subtitle)
            }

            GiftCardFailure.WRONG_NETWORK -> {
                errorStatus(R.string.redeemGift_wrongNetwork_title, R.string.redeemGift_wrongNetwork_subtitle)
            }

            GiftCardFailure.LINK_UNAVAILABLE -> {
                errorStatus(R.string.redeemGift_linkUnavailable_title, R.string.redeemGift_linkUnavailable_subtitle)
            }

            GiftCardFailure.NOT_AVAILABLE -> {
                errorStatus(R.string.redeemGift_notAvailable_title, R.string.redeemGift_notAvailable_subtitle)
            }

            GiftCardFailure.CHECK_FAILED -> {
                retryableStatus(R.string.redeemGift_checkFailed_title, R.string.redeemGift_checkFailed_subtitle)
            }

            GiftCardFailure.REDEEM_FAILED -> {
                retryableStatus(R.string.redeemGift_failure_title, R.string.redeemGift_failure_subtitle)
            }
        }

    private fun errorStatus(
        title: Int,
        subtitle: Int
    ) = RedeemGiftState.status(
        background = ERROR,
        image = imageRes(R.drawable.ic_cloud_eyes),
        title = stringRes(title),
        subtitle = stringRes(subtitle),
        primaryButton = closeButton(ButtonStyle.PRIMARY),
        secondaryButton = null,
        onBack = ::onBack,
    )

    private fun retryableStatus(
        title: Int,
        subtitle: Int
    ) = RedeemGiftState.status(
        background = ERROR,
        image = imageRes(R.drawable.ic_skull),
        title = stringRes(title),
        subtitle = stringRes(subtitle),
        primaryButton = retryButton(R.string.redeemGift_retry),
        secondaryButton = closeButton(),
        onBack = ::onBack,
    )

    private fun retryButton(
        text: Int,
        style: ButtonStyle = ButtonStyle.PRIMARY
    ) = ButtonState(
        text = stringRes(text),
        style = style,
        onClick = ::onRetryClick
    )

    private fun closeButton(style: ButtonStyle = ButtonStyle.SECONDARY) =
        ButtonState(
            text = stringRes(R.string.general_close),
            style = style,
            onClick = ::onBack
        )
}
