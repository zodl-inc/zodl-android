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
import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardHandle
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.common.model.GiftCardSummary
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStore
import co.electriccoin.zcash.ui.common.repository.GiftCardRepository
import co.electriccoin.zcash.ui.common.usecase.GetGiftCardDestinationAddressUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the gift card redeem flow: take the link from [GiftCardLinkStore], parse it, check the card on chain,
 * then sweep it into the selected account's Orchard-only address. The card's temporary wallet is cleaned up
 * whenever this screen goes away, whatever state it is in.
 */
@Suppress("LongParameterList")
class RedeemGiftVM(
    private val args: RedeemGiftArgs,
    private val giftCardLinkStore: GiftCardLinkStore,
    private val giftCardRepository: GiftCardRepository,
    private val getDestinationAddress: GetGiftCardDestinationAddressUseCase,
    private val navigationRouter: NavigationRouter,
    exchangeRateRepository: ExchangeRateRepository,
    getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
) : ViewModel() {
    private val phase = MutableStateFlow<Phase>(Phase.Checking(progress = null))

    private var summary: GiftCardSummary? = null

    private var checkJob: Job? = null

    private var redeemJob: Job? = null

    val state: StateFlow<RedeemGiftState> =
        combine(
            phase,
            exchangeRateRepository.state,
            getSelectedWalletAccount.observe().onStart { emit(null) },
        ) { phase, exchangeRate, account ->
            createState(phase, exchangeRate, account)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = createState(phase.value, null, null)
        )

    init {
        viewModelScope.launch { open() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun open() {
        val link = giftCardLinkStore.take(args.linkId)
        if (link == null) {
            phase.value = Phase.LinkUnavailable
            return
        }
        val parsed =
            try {
                giftCardRepository.parse(link)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                phase.value = e.toPhase()
                return
            }
        summary = parsed
        startCheck(handle = parsed.handle, showProgress = true)
    }

    private fun startCheck(
        handle: GiftCardHandle,
        showProgress: Boolean,
        delayFirstAttempt: Boolean = false
    ) {
        checkJob?.cancel()
        checkJob =
            viewModelScope.launch {
                var isFirstAttempt = true
                do {
                    // Funds were found but are not spendable yet: keep re-checking quietly while the user waits.
                    if (!isFirstAttempt || delayFirstAttempt) delay(PENDING_RETRY_INTERVAL)
                    val result = checkOnce(handle, showProgress = showProgress && isFirstAttempt)
                    phase.value = phaseAfterCheck(result, isQuietRecheck = !isFirstAttempt)
                    isFirstAttempt = false
                } while (phase.value is Phase.Pending)
            }
    }

    private fun phaseAfterCheck(
        result: Result<GiftCardStatus>,
        isQuietRecheck: Boolean
    ): Phase {
        result.getOrNull()?.let { return it.toPhase() }
        val current = phase.value
        // A quiet re-check of a pending card that fails keeps the pending screen and tries again.
        return if (isQuietRecheck && current is Phase.Pending) {
            current.copy(isRechecking = false)
        } else {
            result.exceptionOrNull().toCheckFailurePhase()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun checkOnce(
        handle: GiftCardHandle,
        showProgress: Boolean
    ): Result<GiftCardStatus> {
        if (showProgress) phase.value = Phase.Checking(progress = null)
        val progressJob =
            viewModelScope.launch {
                giftCardRepository.observeCheckProgress(handle).collect { progress ->
                    phase.update { if (it is Phase.Checking) Phase.Checking(progress) else it }
                }
            }
        return try {
            Result.success(giftCardRepository.check(handle))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            progressJob.cancel()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun onRedeemClick() {
        val handle = summary?.handle ?: return
        if (redeemJob?.isActive == true) return
        checkJob?.cancel()
        redeemJob =
            viewModelScope.launch {
                val amount = (phase.value as? Phase.Ready)?.spendable
                phase.value = Phase.Redeeming
                phase.value =
                    try {
                        val txId = giftCardRepository.redeem(handle, getDestinationAddress())
                        Phase.Success(txId = txId, amount = amount)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: GiftCardException) {
                        e.toPhase()
                    } catch (_: Exception) {
                        Phase.RedeemFailed
                    }
            }
    }

    private fun onRetryClick() {
        val handle = summary?.handle ?: return
        val current = phase.value
        if (current is Phase.Pending) {
            recheckPending(handle, current)
            return
        }
        // A failed redeem is retried through a fresh check, so that a send that did reach the network is shown as an
        // empty card rather than attempted twice.
        startCheck(handle = handle, showProgress = true)
    }

    /**
     * "Check again" on a pending card: the card's wallet is already synced, so the check is near-instant and
     * replacing the screen with the progress view would just flicker. Instead the button shows it is working,
     * the pending text is refreshed with the confirmations still needed, and the quiet re-check loop resumes.
     */
    private fun recheckPending(
        handle: GiftCardHandle,
        current: Phase.Pending
    ) {
        if (current.isRechecking) return
        checkJob?.cancel()
        checkJob =
            viewModelScope.launch {
                phase.value = current.copy(isRechecking = true)
                val result = checkOnce(handle, showProgress = false)
                phase.value = phaseAfterCheck(result, isQuietRecheck = true)
                if (phase.value is Phase.Pending) {
                    startCheck(handle = handle, showProgress = false, delayFirstAttempt = true)
                }
            }
    }

    private fun onBack() {
        if (phase.value is Phase.Redeeming) return
        navigationRouter.back()
    }

    private fun onDoneClick() = navigationRouter.backToRoot()

    override fun onCleared() {
        summary?.handle?.let { giftCardRepository.cleanup(it) }
        summary = null
        super.onCleared()
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun createState(
        phase: Phase,
        exchangeRate: ExchangeRateState?,
        account: WalletAccount?
    ): RedeemGiftState =
        when (phase) {
            is Phase.Checking -> {
                status(
                    background = null,
                    image = null,
                    title = stringRes(R.string.redeemGift_checking_title),
                    subtitle =
                        phase.progress?.let {
                            val percent = (it.coerceIn(0f, 1f) * PERCENT).roundToInt()
                            stringRes(R.string.redeemGift_checking_progress, percent)
                        } ?: stringRes(R.string.redeemGift_checking_subtitle),
                    primaryButton = null,
                    secondaryButton = null,
                )
            }

            is Phase.Ready -> {
                RedeemGiftState.Ready(
                    amount = stringRes(phase.spendable),
                    fiatAmount = phase.spendable.toFiat(exchangeRate),
                    message = summary?.message?.takeIf { it.isNotBlank() }?.let { stringRes(it) },
                    destination =
                        stringRes(
                            R.string.redeemGift_ready_destination,
                            account?.name ?: stringRes(R.string.accounts_zashi)
                        ),
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

            is Phase.Pending -> {
                status(
                    background = PENDING,
                    image = R.drawable.ic_face_star,
                    title = stringRes(R.string.redeemGift_pending_title),
                    subtitle = pendingSubtitle(phase),
                    primaryButton =
                        retryButton(R.string.redeemGift_checkAgain).copy(
                            isLoading = phase.isRechecking,
                            isEnabled = !phase.isRechecking
                        ),
                    secondaryButton = closeButton(),
                )
            }

            Phase.Empty -> {
                status(
                    background = null,
                    image = R.drawable.ic_cloud_eyes,
                    title = stringRes(R.string.redeemGift_empty_title),
                    subtitle = stringRes(R.string.redeemGift_empty_subtitle),
                    primaryButton = closeButton(ButtonStyle.PRIMARY),
                    secondaryButton = retryButton(R.string.redeemGift_checkAgain, ButtonStyle.SECONDARY),
                )
            }

            Phase.InvalidLink -> {
                errorStatus(R.string.redeemGift_invalid_title, R.string.redeemGift_invalid_subtitle)
            }

            Phase.WrongNetwork -> {
                errorStatus(R.string.redeemGift_wrongNetwork_title, R.string.redeemGift_wrongNetwork_subtitle)
            }

            Phase.LinkUnavailable -> {
                errorStatus(R.string.redeemGift_linkUnavailable_title, R.string.redeemGift_linkUnavailable_subtitle)
            }

            Phase.NotAvailable -> {
                errorStatus(R.string.redeemGift_notAvailable_title, R.string.redeemGift_notAvailable_subtitle)
            }

            Phase.CheckFailed -> {
                status(
                    background = ERROR,
                    image = R.drawable.ic_skull,
                    title = stringRes(R.string.redeemGift_checkFailed_title),
                    subtitle = stringRes(R.string.redeemGift_checkFailed_subtitle),
                    primaryButton = retryButton(R.string.redeemGift_retry),
                    secondaryButton = closeButton(),
                )
            }

            Phase.Redeeming -> {
                status(
                    background = null,
                    image = null,
                    title = stringRes(R.string.redeemGift_redeeming_title),
                    subtitle = stringRes(R.string.redeemGift_redeeming_subtitle),
                    primaryButton = null,
                    secondaryButton = null,
                )
            }

            is Phase.Success -> {
                status(
                    background = SUCCESS,
                    image = R.drawable.ic_fist_punch,
                    title = stringRes(R.string.redeemGift_success_title),
                    subtitle =
                        phase.amount?.let { stringRes(R.string.redeemGift_success_subtitle, stringRes(it)) }
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

            Phase.RedeemFailed -> {
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
            showAppBar = image != null,
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

    private fun pendingSubtitle(phase: Phase.Pending): StringResource {
        val remaining =
            phase.confirmationsRemaining?.takeIf { it > 0 }
                ?: return stringRes(R.string.redeemGift_pending_subtitle, stringRes(phase.pending))
        val minutes = ((remaining * BLOCK_TIME_SECONDS) + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
        return stringRes(
            R.string.redeemGift_pending_subtitle_confirmations,
            stringRes(phase.pending),
            remaining,
            minutes
        )
    }

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

    private fun GiftCardStatus.toPhase(): Phase =
        when (this) {
            is GiftCardStatus.Ready -> Phase.Ready(spendable)
            is GiftCardStatus.Pending -> Phase.Pending(pending, confirmationsRemaining)
            GiftCardStatus.Empty -> Phase.Empty
        }

    private fun Throwable?.toCheckFailurePhase(): Phase =
        if (this is GiftCardException) toPhase() else Phase.CheckFailed

    private fun Throwable.toPhase(): Phase =
        when (this) {
            is GiftCardException.WrongNetwork -> Phase.WrongNetwork
            is GiftCardException.NotAvailable -> Phase.NotAvailable
            is GiftCardException.UnknownHandle -> Phase.LinkUnavailable
            is GiftCardException.InvalidLink -> Phase.InvalidLink
            is GiftCardException.SubmitFailed -> Phase.RedeemFailed
            else -> Phase.InvalidLink
        }

    private sealed interface Phase {
        data class Checking(
            val progress: Float?
        ) : Phase

        data class Ready(
            val spendable: Zatoshi
        ) : Phase

        data class Pending(
            val pending: Zatoshi,
            val confirmationsRemaining: Int?,
            val isRechecking: Boolean = false
        ) : Phase

        data object Empty : Phase

        data object InvalidLink : Phase

        data object WrongNetwork : Phase

        data object LinkUnavailable : Phase

        data object NotAvailable : Phase

        data object CheckFailed : Phase

        data object Redeeming : Phase

        data class Success(
            val txId: String,
            val amount: Zatoshi?
        ) : Phase

        data object RedeemFailed : Phase
    }

    companion object {
        val PENDING_RETRY_INTERVAL = 30.seconds
        private const val PERCENT = 100
        private const val BLOCK_TIME_SECONDS = 75
        private const val SECONDS_PER_MINUTE = 60
    }
}
