package co.electriccoin.zcash.ui.screen.swap.quote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.QuoteLowAmountException
import co.electriccoin.zcash.ui.common.datasource.SwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_INPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_OUTPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.FLEX_INPUT
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.ResponseWithNearErrorException
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.CancelSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.CancelSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitProposalUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.screen.swap.orconfirmation.ORSwapConfirmationArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

internal class SwapQuoteVM(
    observeProposal: ObserveProposalUseCase,
    applicationStateProvider: ApplicationStateProvider,
    private val swapRepository: SwapRepository,
    private val cancelSwapQuote: CancelSwapQuoteUseCase,
    private val cancelSwap: CancelSwapUseCase,
    private val swapQuoteSuccessMapper: SwapQuoteVMMapper,
    private val submitProposal: SubmitProposalUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    val state: StateFlow<SwapQuoteState?> =
        combine(
            swapRepository.quote.filterNotNull(),
            observeProposal.observeNullable(),
        ) { quote, proposal ->
            when (quote) {
                SwapQuoteData.Loading -> null
                is SwapQuoteData.Error -> createErrorState(quote)
                is SwapQuoteData.Success -> createState(proposal, quote)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    init {
        applicationStateProvider
            .observeOnForeground()
            .onEach {
                val quote = (swapRepository.quote.value as? SwapQuoteData.Success)?.quote ?: return@onEach

                if ((Clock.System.now() - quote.timestamp) >= 3.minutes) {
                    cancelSwapQuote()
                }
            }.launchIn(viewModelScope)
    }

    private fun createState(
        proposal: TransactionProposal?,
        quote: SwapQuoteData.Success
    ): SwapQuoteState.Success =
        swapQuoteSuccessMapper.createState(
            state = SwapQuoteInternalState(proposal as? SwapTransactionProposal, quote.quote),
            onBack = ::onBack,
            onSubmitQuoteClick = ::onSubmitQuoteClick,
            onNavigateToOnRampSwap = ::onNavigateToOnRampSwap
        )

    private fun onNavigateToOnRampSwap() = navigationRouter.forward(ORSwapConfirmationArgs)

    private fun createErrorState(quote: SwapQuoteData.Error): SwapQuoteState.Error {
        val message =
            when {
                quote.exception is QuoteLowAmountException &&
                    quote.exception.amountFormatted != null -> {
                    stringRes(
                        R.string.swap_quote_error_too_low_try_at_least,
                        stringResByDynamicCurrencyNumber(
                            amount = quote.exception.amountFormatted,
                            ticker = quote.exception.asset.tokenTicker
                        )
                    )
                }

                quote.exception is QuoteLowAmountException -> {
                    stringRes(R.string.swap_quote_error_too_low_try_higher)
                }

                quote.exception is ResponseWithNearErrorException &&
                    !quote.exception.error.message
                        .contains("failed to get quote", ignoreCase = true) -> {
                    stringRes(quote.exception.error.message)
                }

                else -> {
                    when (quote.mode) {
                        EXACT_INPUT, FLEX_INPUT -> stringRes(R.string.swap_quoteUnavailableSwap)
                        EXACT_OUTPUT -> stringRes(R.string.swap_quoteUnavailable)
                    }
                }
            }

        return SwapQuoteState.Error(
            icon = imageRes(R.drawable.ic_swap_quote_error),
            title = stringRes(R.string.swapAndPay_quoteUnavailable),
            subtitle = message,
            negativeButton =
                ButtonState(
                    text =
                        when (quote.mode) {
                            EXACT_INPUT, FLEX_INPUT -> stringRes(R.string.swap_quote_cancel_swap)
                            EXACT_OUTPUT -> stringRes(R.string.swapAndPay_cancelPayment)
                        },
                    onClick = ::onCancelPaymentClick
                ),
            positiveButton =
                ButtonState(
                    text =
                        when (quote.mode) {
                            EXACT_INPUT, FLEX_INPUT -> stringRes(R.string.swapAndPay_editSwap)
                            EXACT_OUTPUT -> stringRes(R.string.swapAndPay_editPayment)
                        },
                    onClick = ::onEditPaymentClick
                ),
            onBack = ::onBackDuringError
        )
    }

    private fun onEditPaymentClick() = cancelSwapQuote()

    private fun onBack() = cancelSwapQuote()

    private fun onBackDuringError() = cancelSwapQuote()

    private fun onSubmitQuoteClick() = viewModelScope.launch { submitProposal() }

    private fun onCancelPaymentClick() = cancelSwap()
}

internal data class SwapQuoteInternalState(
    val proposal: SwapTransactionProposal?,
    val quote: SwapQuote,
) {
    val total: BigDecimal = quote.getTotal(proposal?.proposal)
    val totalUsd: BigDecimal = quote.getTotalUsd(proposal?.proposal)
    val totalFees = quote.affiliateFee
    val totalFeesZatoshi: Zatoshi = quote.getTotalFeesZatoshi(proposal?.proposal)
    val totalFeesUsd: BigDecimal = quote.getTotalFeesUsd(proposal?.proposal)
}
