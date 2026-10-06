@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.GiftCardFailure
import co.electriccoin.zcash.ui.common.model.GiftCardPhase
import co.electriccoin.zcash.ui.common.model.GiftCardSession
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.GiftCardRepository
import co.electriccoin.zcash.ui.common.usecase.GetGiftCardDestinationAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.TickerLocation
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.loadingImageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState.Background.ERROR
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState.Background.PENDING
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState.Background.SUCCESS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.math.MathContext

/**
 * The gift card redeem screen. The redemption itself is a session of [GiftCardRepository], which runs it
 * independently of this screen: this view model only shows the session and forwards the user's intents. Leaving the
 * screen with back or close ends the session (except while the card is being redeemed, when back does nothing);
 * the screen merely going away does not.
 */
@Suppress("LongParameterList")
class RedeemGiftVM(
    private val args: RedeemGiftArgs,
    private val giftCardRepository: GiftCardRepository,
    private val getDestinationAddress: GetGiftCardDestinationAddressUseCase,
    private val navigationRouter: NavigationRouter,
    exchangeRateRepository: ExchangeRateRepository,
    getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
) : ViewModel() {
    val state: StateFlow<RedeemGiftState?> =
        combine(
            giftCardRepository.observeSession(args.linkId),
            exchangeRateRepository.state,
            getSelectedWalletAccount.observe().onStart { emit(null) },
        ) { session, exchangeRate, account ->
            createState(session, exchangeRate, account)
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

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun createState(
        session: GiftCardSession,
        exchangeRate: ExchangeRateState?,
        account: WalletAccount?
    ): RedeemGiftState =
        when (val phase = session.phase) {
            GiftCardPhase.Checking -> {
                status(
                    background = null,
                    image = null,
                    title = stringRes(R.string.redeemGift_checking_title),
                    subtitle = stringRes(R.string.redeemGift_checking_subtitle),
                    primaryButton = null,
                    secondaryButton = null,
                    showAppBar = true,
                )
            }

            is GiftCardPhase.Ready -> {
                RedeemGiftState.Ready(
                    title = stringRes(R.string.redeemGift_title),
                    image = ImageResource.ByDrawable(R.drawable.ic_integrations_gift),
                    heading = stringRes(R.string.redeemGift_ready_title),
                    amount = stringRes(phase.redeemable),
                    fiatAmount = phase.redeemable.toFiat(exchangeRate),
                    feeHint = stringRes(R.string.redeemGift_ready_feeHint),
                    messageLabel = stringRes(R.string.redeemGift_ready_messageLabel),
                    message =
                        session.summary
                            ?.message
                            ?.takeIf { it.isNotBlank() }
                            ?.let { stringRes(it) },
                    destination = account?.name?.let { stringRes(R.string.redeemGift_ready_destination, it) },
                    redeemButton =
                        ButtonState(
                            text = stringRes(R.string.redeemGift_redeem),
                            style = ButtonStyle.PRIMARY,
                            hapticFeedbackType = HapticFeedbackType.Confirm,
                            onClick = ::onRedeemClick
                        ),
                    onBack = ::onBack
                )
            }

            is GiftCardPhase.Pending -> {
                status(
                    background = PENDING,
                    image = R.drawable.ic_face_star,
                    title = stringRes(R.string.redeemGift_pending_title),
                    subtitle = stringRes(R.string.redeemGift_pending_subtitle, stringRes(phase.pending)),
                    primaryButton =
                        retryButton(R.string.redeemGift_checkAgain).copy(
                            isLoading = phase.isRechecking,
                            isEnabled = !phase.isRechecking
                        ),
                    secondaryButton = closeButton(),
                )
            }

            GiftCardPhase.Empty -> {
                status(
                    background = null,
                    image = R.drawable.ic_cloud_eyes,
                    title = stringRes(R.string.redeemGift_empty_title),
                    subtitle = stringRes(R.string.redeemGift_empty_subtitle),
                    primaryButton = closeButton(ButtonStyle.PRIMARY),
                    secondaryButton = retryButton(R.string.redeemGift_checkAgain, ButtonStyle.SECONDARY),
                )
            }

            GiftCardPhase.Redeeming -> {
                status(
                    background = null,
                    image = null,
                    title = stringRes(R.string.redeemGift_redeeming_title),
                    subtitle = stringRes(R.string.redeemGift_redeeming_subtitle),
                    primaryButton = null,
                    secondaryButton = null,
                    onBack = {},
                )
            }

            is GiftCardPhase.Redeemed -> {
                status(
                    background = SUCCESS,
                    image = R.drawable.ic_fist_punch,
                    title = stringRes(R.string.redeemGift_success_title),
                    subtitle =
                        phase.redemption.received
                            ?.let { stringRes(R.string.redeemGift_success_subtitle, stringRes(it)) }
                            ?: stringRes(R.string.redeemGift_success_subtitle_noAmount),
                    primaryButton =
                        ButtonState(
                            text = stringRes(R.string.general_close),
                            style = ButtonStyle.PRIMARY,
                            onClick = ::onDoneClick
                        ),
                    secondaryButton = null,
                    onBack = ::onDoneClick
                )
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
                status(
                    background = ERROR,
                    image = R.drawable.ic_skull,
                    title = stringRes(R.string.redeemGift_checkFailed_title),
                    subtitle = stringRes(R.string.redeemGift_checkFailed_subtitle),
                    primaryButton = retryButton(R.string.redeemGift_retry),
                    secondaryButton = closeButton(),
                )
            }

            GiftCardFailure.REDEEM_FAILED -> {
                status(
                    background = ERROR,
                    image = R.drawable.ic_skull,
                    title = stringRes(R.string.redeemGift_failure_title),
                    subtitle = stringRes(R.string.redeemGift_failure_subtitle),
                    primaryButton = retryButton(R.string.redeemGift_retry),
                    secondaryButton = closeButton(),
                )
            }
        }

    @Suppress("LongParameterList")
    private fun status(
        background: TransactionProgressState.Background?,
        image: Int?,
        title: StringResource,
        subtitle: StringResource,
        primaryButton: ButtonState?,
        secondaryButton: ButtonState?,
        onBack: () -> Unit = ::onBack,
        showAppBar: Boolean = image != null,
    ) = RedeemGiftState.Status(
        TransactionProgressState(
            background = background,
            image = image?.let { imageRes(it) } ?: loadingImageRes(),
            title = title,
            subtitle = subtitle.withStyle(),
            middleButton = null,
            primaryButton = primaryButton,
            secondaryButton = secondaryButton,
            onBack = onBack,
            showAppBar = showAppBar,
            centerContent = true,
        )
    )

    private fun errorStatus(
        title: Int,
        subtitle: Int
    ) = status(
        background = ERROR,
        image = R.drawable.ic_cloud_eyes,
        title = stringRes(title),
        subtitle = stringRes(subtitle),
        primaryButton = closeButton(ButtonStyle.PRIMARY),
        secondaryButton = null,
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

    private fun Zatoshi.toFiat(exchangeRate: ExchangeRateState?): StringResource? {
        val data = exchangeRate as? ExchangeRateState.Data
        val conversion = data?.currencyConversion ?: return null
        return stringResByDynamicCurrencyNumber(
            amount =
                convertZatoshiToZec().multiply(
                    BigDecimal(conversion.priceOfZec),
                    MathContext.DECIMAL128
                ),
            ticker = data.expectedCurrency.symbol,
            tickerLocation = TickerLocation.BEFORE
        )
    }
}
