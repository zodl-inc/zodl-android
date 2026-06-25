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
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.model.near.requireMatchingAsset
import co.electriccoin.zcash.ui.common.model.near.requireQuoteMatchesUserAmount
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
        val expectedOrigin = swapRepository.assets.value.zecAsset
        val supportedAssets = swapRepository.assets.value.data
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
            validateQuote = { quote ->
                requireQuoteMatchesUserAmount(
                    quoted = quote.amountInFormatted,
                    requested = amount,
                    decimals = quote.originAsset.decimals
                )
                requireExpectedAsset(
                    name = "originAsset",
                    expected = expectedOrigin,
                    actual = quote.originAsset
                )
                requireSupportedSelectedAsset(
                    name = "destinationAsset",
                    supportedAssets = supportedAssets,
                    selectedAsset = selectedAsset,
                    actual = quote.destinationAsset
                )
                requireMatchingAddress(
                    name = "destinationAddress",
                    expected = address,
                    actual = quote.destinationAddress.address
                )
                requireMatchingAddress(
                    name = "refundAddress",
                    expected = newAddress.address,
                    actual = quote.refundAddress.address
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
        val expectedOrigin = swapRepository.assets.value.zecAsset
        val supportedAssets = swapRepository.assets.value.data
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
            validateQuote = { quote ->
                requireQuoteMatchesUserAmount(
                    quoted = quote.amountOutFormatted,
                    requested = amount,
                    decimals = quote.destinationAsset.decimals
                )
                requireExpectedAsset(
                    name = "originAsset",
                    expected = expectedOrigin,
                    actual = quote.originAsset
                )
                requireSupportedSelectedAsset(
                    name = "destinationAsset",
                    supportedAssets = supportedAssets,
                    selectedAsset = selectedAsset,
                    actual = quote.destinationAsset
                )
                requireMatchingAddress(
                    name = "destinationAddress",
                    expected = address,
                    actual = quote.destinationAddress.address
                )
                requireMatchingAddress(
                    name = "refundAddress",
                    expected = newAddress.address,
                    actual = quote.refundAddress.address
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
        val expectedDestination = swapRepository.assets.value.zecAsset
        val supportedAssets = swapRepository.assets.value.data
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
            validateQuote = { quote ->
                requireQuoteMatchesUserAmount(
                    quoted = quote.amountInFormatted,
                    requested = amount,
                    decimals = quote.originAsset.decimals
                )
                requireSupportedSelectedAsset(
                    name = "originAsset",
                    supportedAssets = supportedAssets,
                    selectedAsset = selectedAsset,
                    actual = quote.originAsset
                )
                requireExpectedAsset(
                    name = "destinationAsset",
                    expected = expectedDestination,
                    actual = quote.destinationAsset
                )
                requireMatchingAddress(
                    name = "refundAddress",
                    expected = refundAddress,
                    actual = quote.refundAddress.address
                )
                requireMatchingAddress(
                    name = "destinationAddress",
                    expected = newAddress.address,
                    actual = quote.destinationAddress.address
                )
            },
            createProposal = false,
            canNavigateToSwapQuote = canNavigateToSwapQuote
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun requestQuote(
        requestQuote: suspend () -> Unit,
        validateQuote: (SwapQuote) -> Unit,
        createProposal: Boolean,
        canNavigateToSwapQuote: () -> Boolean
    ) = withContext(Dispatchers.Default) {
        requestQuote()

        val result = swapRepository.quote.first { it !in listOf(null, SwapQuoteData.Loading) }

        if (result is SwapQuoteData.Success) {
            try {
                validateQuote(result.quote)
                if (createProposal) {
                    createProposal(result.quote)
                }
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

    /**
     * Asserts the quote's ZEC-side asset matches `expected` — an independent snapshot of
     * `assets.value.zecAsset` — so this is a genuine cross-check that the repository used the right ZEC
     * asset. Combined with [co.electriccoin.zcash.ui.common.model.near.NearSwapQuote]'s `init`
     * requested-vs-returned check (which guards the *server* substituting the asset), the two cover the
     * full path. The user-selected side is validated separately and more strictly by
     * [requireSupportedSelectedAsset].
     */
    private fun requireExpectedAsset(name: String, expected: SwapAsset?, actual: SwapAsset) {
        if (expected == null) return
        requireMatchingAsset(
            name = name,
            expectedTokenTicker = expected.tokenTicker,
            expectedChainTicker = expected.chainTicker,
            actual = actual
        )
    }

    /**
     * Stricter counterpart of [requireExpectedAsset] for the user-selected asset. It looks the
     * selection up in [supportedAssets] (an independent snapshot of `SwapRepository.assets.value.data`)
     * by id, requiring it to still be a currently-supported asset, then matches the quote against that
     * canonical record. This catches a stale/unknown selected asset and a selected asset whose
     * ticker/chain disagrees with the supported record.
     */
    private fun requireSupportedSelectedAsset(
        name: String,
        supportedAssets: List<SwapAsset>?,
        selectedAsset: SwapAsset,
        actual: SwapAsset
    ) {
        val supported = supportedAssets?.firstOrNull { it.assetId == selectedAsset.assetId }
        requireNotNull(supported) {
            "Swap quote asset mismatch: $name=${selectedAsset.assetId} is not a currently-supported swap asset"
        }
        requireMatchingAsset(
            name = name,
            expectedTokenTicker = supported.tokenTicker,
            expectedChainTicker = supported.chainTicker,
            actual = actual
        )
    }

    private fun requireMatchingAddress(name: String, expected: String, actual: String) {
        require(expected == actual) {
            "Swap quote address mismatch: expected $name=$expected but quote returned $actual"
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
