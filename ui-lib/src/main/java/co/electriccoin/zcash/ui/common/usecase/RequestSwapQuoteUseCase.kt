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
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_INPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_OUTPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.FLEX_INPUT
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.SwapQuoteMismatchException
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.insufficientfunds.InsufficientFundsArgs
import co.electriccoin.zcash.ui.screen.swap.mismatch.SwapQuoteMismatchArgs
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
    private val accountDataSource: AccountDataSource,
    private val synchronizerProvider: SynchronizerProvider,
) {
    suspend fun requestExactInput(
        amount: BigDecimal,
        address: String,
        selectedAsset: SwapAsset,
        slippage: BigDecimal,
        canNavigateToSwapQuote: () -> Boolean
    ) {
        val newAddress = accountDataSource.requestNextShieldedAddress()
        requestQuote(
            requestQuote = {
                swapRepository.requestExactInputQuote(
                    amount = amount,
                    address = address,
                    refundAddress = newAddress.address,
                    destinationAsset = selectedAsset,
                    slippage = slippage
                )
            },
            selectedAsset = selectedAsset,
            createProposal = true,
            canNavigateToSwapQuote = canNavigateToSwapQuote
        )
    }

    suspend fun requestExactOutput(
        amount: BigDecimal,
        address: String,
        selectedAsset: SwapAsset,
        slippage: BigDecimal,
        canNavigateToSwapQuote: () -> Boolean
    ) {
        val newAddress = accountDataSource.requestNextShieldedAddress()
        requestQuote(
            requestQuote = {
                swapRepository.requestExactOutputQuote(
                    amount = amount,
                    address = address,
                    refundAddress = newAddress.address,
                    destinationAsset = selectedAsset,
                    slippage = slippage
                )
            },
            selectedAsset = selectedAsset,
            createProposal = true,
            canNavigateToSwapQuote = canNavigateToSwapQuote
        )
    }

    suspend fun requestFlexInputIntoZec(
        amount: BigDecimal,
        refundAddress: String,
        selectedAsset: SwapAsset,
        slippage: BigDecimal,
        canNavigateToSwapQuote: () -> Boolean
    ) {
        val newAddress = accountDataSource.requestNextShieldedAddress()
        requestQuote(
            requestQuote = {
                swapRepository
                    .requestFlexInputIntoZec(
                        amount = amount,
                        refundAddress = refundAddress,
                        destinationAddress = newAddress.address,
                        originAsset = selectedAsset,
                        slippage = slippage
                    )
            },
            selectedAsset = selectedAsset,
            createProposal = false,
            canNavigateToSwapQuote = canNavigateToSwapQuote
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun requestQuote(
        requestQuote: suspend () -> Unit,
        selectedAsset: SwapAsset,
        createProposal: Boolean,
        canNavigateToSwapQuote: () -> Boolean
    ) = withContext(Dispatchers.Default) {
        requestQuote()

        val result = swapRepository.quote.first { it !in listOf(null, SwapQuoteData.Loading) }

        val mismatch = (result as? SwapQuoteData.Error)?.exception as? SwapQuoteMismatchException
        if (mismatch != null) {
            swapRepository.clearQuote()
            if (canNavigateToSwapQuote()) {
                navigationRouter.forward(mismatchArgs(mismatch, result.mode, selectedAsset))
            }
            return@withContext
        }

        if (result is SwapQuoteData.Success && createProposal) {
            try {
                createProposal(result.quote)
            } catch (_: TexUnsupportedOnKSException) {
                swapRepository.clearQuote()
                zashiProposalRepository.clear()
                keystoneProposalRepository.clear()
                navigationRouter.forward(TEXUnsupportedArgs)
                return@withContext
            } catch (_: InsufficientFundsException) {
                swapRepository.clearQuote()
                zashiProposalRepository.clear()
                keystoneProposalRepository.clear()
                navigationRouter.forward(InsufficientFundsArgs)
                return@withContext
            } catch (e: Exception) {
                swapRepository.clearQuote()
                zashiProposalRepository.clear()
                keystoneProposalRepository.clear()
                navigateToErrorUseCase(ErrorArgs.General(e))
                return@withContext
            }
        }

        if (canNavigateToSwapQuote()) {
            navigationRouter.forward(SwapQuoteArgs)
        }
    }

    /**
     * The mismatch sheet's arguments. The quote itself was rejected, so the assets come from the request:
     * ZEC is the origin except in a flex-input swap, where the user-selected asset is what is being sold.
     */
    private fun mismatchArgs(
        exception: SwapQuoteMismatchException,
        mode: SwapMode,
        selectedAsset: SwapAsset
    ): SwapQuoteMismatchArgs {
        val zecAsset = swapRepository.assets.value.zecAsset
        val origin = if (mode == FLEX_INPUT) selectedAsset else zecAsset
        val destination = if (mode == FLEX_INPUT) zecAsset else selectedAsset
        return SwapQuoteMismatchArgs(
            provider = SWAP_PROVIDER_NEAR,
            mode = mode,
            originTokenTicker = origin?.tokenTicker ?: ZEC_TICKER,
            originChainTicker = origin?.chainTicker ?: ZEC_TICKER,
            destinationTokenTicker = destination?.tokenTicker ?: ZEC_TICKER,
            destinationChainTicker = destination?.chainTicker ?: ZEC_TICKER,
            mismatchType = exception.type,
            depositAddress = exception.depositAddress
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun createProposal(quote: SwapQuote) {
        val send =
            ZecSend(
                destination = getWalletAddress(quote.depositAddress.address),
                amount = quote.amountIn.toExactQuoteZatoshi(),
                memo = Memo(""),
                proposal = null
            )

        when (accountDataSource.getSelectedAccount()) {
            is KeystoneAccount -> {
                when (quote.mode) {
                    EXACT_INPUT -> keystoneProposalRepository.createExactInputSwapProposal(send, quote)
                    EXACT_OUTPUT -> keystoneProposalRepository.createExactOutputSwapProposal(send, quote)
                    FLEX_INPUT -> throw UnsupportedOperationException("Flex input swap not supported")
                }
                keystoneProposalRepository.createPCZTFromProposal()
            }

            is ZashiAccount -> {
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

/** The only swap provider the app talks to; matches `NearSwapQuote.provider`. */
internal const val SWAP_PROVIDER_NEAR = "near"

private const val ZEC_TICKER = "zec"

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
