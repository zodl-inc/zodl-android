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
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_INPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_OUTPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.FLEX_INPUT
import co.electriccoin.zcash.ui.common.model.SwapProvider
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.insufficientfunds.InsufficientFundsArgs
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
        val newAddress = accountDataSource.getSelectedAccount().transparent.address
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
        val newAddress = accountDataSource.getSelectedAccount().transparent.address
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
        val newAddress = accountDataSource.getSelectedAccount().transparent.address
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
            createProposal = false,
            canNavigateToSwapQuote = canNavigateToSwapQuote
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun requestQuote(
        requestQuote: suspend () -> Unit,
        createProposal: Boolean,
        canNavigateToSwapQuote: () -> Boolean
    ) = withContext(Dispatchers.Default) {
        requestQuote()

        val result = swapRepository.quote.first { it !in listOf(null, SwapQuoteData.Loading) }

        if (result is SwapQuoteData.Success && createProposal) {
            try {
                createProposal(result.quote)
            } catch (_: TexUnsupportedOnKSException) {
                navigationRouter.forward(TEXUnsupportedArgs)
                keystoneProposalRepository.clear()
                zashiProposalRepository.clear()
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

    @Suppress("TooGenericExceptionCaught")
    private suspend fun createProposal(quote: SwapQuote) {
        // Phase 1 (MOB-1396): Maya execution is gated on SDK OP_RETURN support, so don't build a ZEC deposit
        // proposal for a Maya-selected quote. The quote is still shown; confirm stays disabled for Maya until
        // Phase 2. See docs/SwapKit Spec (Maya DEX).md.
        if (quote.provider == SwapProvider.MAYA) return
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
