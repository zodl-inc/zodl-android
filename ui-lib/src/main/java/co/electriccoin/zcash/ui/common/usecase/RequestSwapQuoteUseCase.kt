package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.InsufficientFundsException
import co.electriccoin.zcash.ui.common.datasource.TexUnsupportedOnKSException
import co.electriccoin.zcash.ui.common.ledger.LedgerProposalPipeline
import co.electriccoin.zcash.ui.common.model.AccountOperationUnsupportedException
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_INPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_OUTPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.FLEX_INPUT
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.isProposalTooLarge
import co.electriccoin.zcash.ui.common.model.requireSpendable
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.insufficientfunds.InsufficientFundsArgs
import co.electriccoin.zcash.ui.screen.swap.mismatch.toMismatchArgs
import co.electriccoin.zcash.ui.screen.swap.quote.SwapQuoteArgs
import co.electriccoin.zcash.ui.screen.texunsupported.TEXUnsupportedArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class RequestSwapQuoteUseCase(
    private val navigationRouter: NavigationRouter,
    private val navigateToErrorUseCase: NavigateToErrorUseCase,
    private val swapRepository: SwapRepository,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val keystoneProposalRepository: KeystoneProposalRepository,
    private val ledgerProposalPipeline: LedgerProposalPipeline,
    private val accountDataSource: AccountDataSource,
    private val synchronizerProvider: SynchronizerProvider,
    private val navigateToTooManyNotes: NavigateToTooManyNotesUseCase,
) {
    suspend fun requestExactInput(
        amount: BigDecimal,
        address: String,
        selectedAsset: SwapAsset,
        slippage: BigDecimal,
        canNavigateToSwapQuote: () -> Boolean
    ): TooManyNotesOutcome? {
        val newAddress = accountDataSource.requestNextShieldedAddress()
        return requestQuote(
            requestQuote = {
                swapRepository.requestExactInputQuote(
                    amount = amount,
                    address = address,
                    refundAddress = newAddress,
                    destinationAsset = selectedAsset,
                    slippage = slippage
                )
            },
            createProposal = true,
            canNavigateToSwapQuote = canNavigateToSwapQuote
        )
    }

    /**
     * @param reviseAmount called with each successful quote, before its proposal is created. A non-null result is a
     * new [amount] to quote instead (Pay's Max, whose fill the provider fee pushes past what is spendable): the quote
     * is requested again for it, up to [maxRevisions] times. A null result keeps the quote, which proceeds as usual.
     * @param isRevisedQuoteAcceptable called with the last re-quoted successful quote, only after a revision. If it
     * returns false the request stops there: the quote is cleared, no proposal is created and nothing is navigated to
     * (the caller has already told the user why).
     * @param maxRevisions how many times the amount may be revised; the quote after the last one is only checked by
     * [isRevisedQuoteAcceptable]
     */
    suspend fun requestExactOutput(
        amount: BigDecimal,
        address: String,
        selectedAsset: SwapAsset,
        slippage: BigDecimal,
        canNavigateToSwapQuote: () -> Boolean,
        reviseAmount: suspend (SwapQuote) -> BigDecimal? = { null },
        isRevisedQuoteAcceptable: suspend (SwapQuote) -> Boolean = { true },
        maxRevisions: Int = 1
    ): TooManyNotesOutcome? {
        val newAddress = accountDataSource.requestNextShieldedAddress()
        var requestedAmount = amount
        return requestQuote(
            requestQuote = {
                swapRepository.requestExactOutputQuote(
                    amount = requestedAmount,
                    address = address,
                    refundAddress = newAddress,
                    destinationAsset = selectedAsset,
                    slippage = slippage
                )
            },
            createProposal = true,
            canNavigateToSwapQuote = canNavigateToSwapQuote,
            reviseQuote = { quote -> reviseAmount(quote)?.also { requestedAmount = it } != null },
            isRevisedQuoteAcceptable = isRevisedQuoteAcceptable,
            maxRevisions = maxRevisions
        )
    }

    suspend fun requestFlexInputIntoZec(
        amount: BigDecimal,
        refundAddress: String,
        selectedAsset: SwapAsset,
        slippage: BigDecimal,
        canNavigateToSwapQuote: () -> Boolean
    ): TooManyNotesOutcome? {
        val newAddress = accountDataSource.requestNextShieldedAddress()
        return requestQuote(
            requestQuote = {
                swapRepository
                    .requestFlexInputIntoZec(
                        amount = amount,
                        refundAddress = refundAddress,
                        destinationAddress = newAddress,
                        originAsset = selectedAsset,
                        slippage = slippage
                    )
            },
            createProposal = false,
            canNavigateToSwapQuote = canNavigateToSwapQuote
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun requestQuote(
        requestQuote: suspend () -> Unit,
        createProposal: Boolean,
        canNavigateToSwapQuote: () -> Boolean,
        reviseQuote: suspend (SwapQuote) -> Boolean = { false },
        isRevisedQuoteAcceptable: suspend (SwapQuote) -> Boolean = { true },
        maxRevisions: Int = 1
    ): TooManyNotesOutcome? =
        withContext(Dispatchers.Default) {
            requestQuote()

            val (result, revisions) = reviseWhileOver(requestQuote, reviseQuote, maxRevisions)
            if (revisions > 0 && result is SwapQuoteData.Success && !isRevisedQuoteAcceptable(result.quote)) {
                swapRepository.clearQuote()
                return@withContext null
            }

            val mismatchArgs = (result as? SwapQuoteData.Error)?.toMismatchArgs()
            if (mismatchArgs != null) {
                if (canNavigateToSwapQuote()) {
                    swapRepository.clearQuote()
                    navigationRouter.forward(mismatchArgs)
                }
                return@withContext null
            }

            if (result is SwapQuoteData.Success && createProposal) {
                try {
                    createProposal(result.quote)
                } catch (_: TexUnsupportedOnKSException) {
                    swapRepository.clearQuote()
                    zashiProposalRepository.clear()
                    keystoneProposalRepository.clear()
                    ledgerProposalPipeline.clear()
                    val isLedger = accountDataSource.getSelectedAccount() is LedgerAccount
                    navigationRouter.forward(TEXUnsupportedArgs(isLedger = isLedger))
                    return@withContext null
                } catch (_: InsufficientFundsException) {
                    swapRepository.clearQuote()
                    zashiProposalRepository.clear()
                    keystoneProposalRepository.clear()
                    ledgerProposalPipeline.clear()
                    navigationRouter.forward(InsufficientFundsArgs)
                    return@withContext null
                } catch (e: Exception) {
                    swapRepository.clearQuote()
                    zashiProposalRepository.clear()
                    keystoneProposalRepository.clear()
                    ledgerProposalPipeline.clear()
                    if (e.isProposalTooLarge()) {
                        return@withContext navigateToTooManyNotes()
                    } else {
                        navigateToErrorUseCase(ErrorArgs.General(e))
                    }
                    return@withContext null
                }
            }

            if (canNavigateToSwapQuote()) {
                navigationRouter.forward(SwapQuoteArgs)
            }
            null
        }

    /**
     * The first quote, then, while [reviseQuote] asks for a new amount and fewer than [maxRevisions] revisions were
     * made, the quote for the revised amount. The quote after the last allowed revision is not offered for another one.
     *
     * @return the last quote result and the number of revisions made
     */
    private suspend fun reviseWhileOver(
        requestQuote: suspend () -> Unit,
        reviseQuote: suspend (SwapQuote) -> Boolean,
        maxRevisions: Int
    ): Pair<SwapQuoteData?, Int> {
        var result = swapRepository.quote.first { it !in listOf(null, SwapQuoteData.Loading) }
        var revisions = 0
        while (result is SwapQuoteData.Success && revisions < maxRevisions && reviseQuote(result.quote)) {
            revisions++
            swapRepository.clearQuote()
            requestQuote()
            result = swapRepository.quote.first { it !in listOf(null, SwapQuoteData.Loading) }
        }
        return result to revisions
    }

    /**
     * Spends from the selected account, so an account that cannot spend is refused before anything
     * is derived from the quote.
     */
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private suspend fun createProposal(quote: SwapQuote) {
        val account = accountDataSource.getSelectedAccount().requireSpendable()
        val send =
            ZecSend(
                destination = getWalletAddress(quote.depositAddress.address),
                amount = quote.amountIn.toExactQuoteZatoshi(),
                memo = Memo(""),
                proposal = null
            )

        when (account) {
            is KeystoneAccount -> {
                when (quote.mode) {
                    EXACT_INPUT -> keystoneProposalRepository.createExactInputSwapProposal(send, quote)
                    EXACT_OUTPUT -> keystoneProposalRepository.createExactOutputSwapProposal(send, quote)
                    FLEX_INPUT -> throw UnsupportedOperationException("Flex input swap not supported")
                }
                keystoneProposalRepository.createPCZTFromProposal()
            }

            is LedgerAccount -> {
                when (quote.mode) {
                    EXACT_INPUT -> ledgerProposalPipeline.createExactInputSwapProposal(send, quote)
                    EXACT_OUTPUT -> ledgerProposalPipeline.createExactOutputSwapProposal(send, quote)
                    FLEX_INPUT -> throw UnsupportedOperationException("Flex input swap not supported")
                }
                ledgerProposalPipeline.createPCZTFromProposal()
            }

            is ViewOnlyAccount -> {
                throw AccountOperationUnsupportedException.viewOnly()
            }

            is ZodlAccount -> {
                when (quote.mode) {
                    EXACT_INPUT -> zashiProposalRepository.createExactInputSwapProposal(send, quote)
                    EXACT_OUTPUT -> zashiProposalRepository.createExactOutputSwapProposal(send, quote)
                    FLEX_INPUT -> throw UnsupportedOperationException("Flex input swap not supported")
                }
            }
        }
    }

    private suspend fun getWalletAddress(address: String): WalletAddress =
        when (val result = synchronizerProvider.getSynchronizer().validateAddress(address)) {
            AddressType.Shielded -> WalletAddress.Sapling.new(address)
            AddressType.Tex -> WalletAddress.Tex.new(address)
            AddressType.Transparent -> WalletAddress.Transparent.new(address)
            AddressType.Unified -> WalletAddress.Unified.new(address)
            is AddressType.Invalid -> throw IllegalStateException(result.reason)
        }
}

private fun BigDecimal.toExactQuoteZatoshi(): Zatoshi =
    try {
        Zatoshi(longValueExact())
    } catch (e: ArithmeticException) {
        throw InvalidSwapQuoteAmountException(this, e)
    }

private class InvalidSwapQuoteAmountException(
    val amount: BigDecimal,
    cause: ArithmeticException
) : IllegalArgumentException("Swap quote amount must be an exact zatoshi value: $amount", cause)
