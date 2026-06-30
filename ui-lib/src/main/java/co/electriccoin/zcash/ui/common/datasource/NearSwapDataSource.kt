package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.crash.android.GlobalCrashReporter
import co.electriccoin.zcash.ui.common.model.DynamicSwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.SwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.ZcashShieldedSwapAddress
import co.electriccoin.zcash.ui.common.model.ZcashSwapAddress
import co.electriccoin.zcash.ui.common.model.ZcashTransparentSwapAddress
import co.electriccoin.zcash.ui.common.model.isZCashAsset
import co.electriccoin.zcash.ui.common.model.near.AppFee
import co.electriccoin.zcash.ui.common.model.near.NearSwapAsset
import co.electriccoin.zcash.ui.common.model.near.NearSwapQuote
import co.electriccoin.zcash.ui.common.model.near.NearSwapQuoteStatus
import co.electriccoin.zcash.ui.common.model.near.NearTokenDto
import co.electriccoin.zcash.ui.common.model.near.QuoteRequest
import co.electriccoin.zcash.ui.common.model.near.QuoteResponseDto
import co.electriccoin.zcash.ui.common.model.near.RecipientType
import co.electriccoin.zcash.ui.common.model.near.RefundType
import co.electriccoin.zcash.ui.common.model.near.SubmitDepositTransactionRequest
import co.electriccoin.zcash.ui.common.model.near.SwapAmountInconsistencyException
import co.electriccoin.zcash.ui.common.model.near.SwapType
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.common.provider.NearApiProvider
import co.electriccoin.zcash.ui.common.provider.ResponseWithNearErrorException
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.provider.TokenIconProvider
import co.electriccoin.zcash.ui.common.provider.TokenNameProvider
import co.electriccoin.zcash.ui.util.loggableNot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class NearSwapDataSource(
    private val nearApiProvider: NearApiProvider,
    private val tokenIconProvider: TokenIconProvider,
    private val tokenNameProvider: TokenNameProvider,
    private val blockchainProvider: BlockchainProvider,
    private val synchronizerProvider: SynchronizerProvider,
) : SwapDataSource {
    private val log = loggableNot("NearSwapDataSourceImpl")

    override suspend fun getSupportedTokens(): List<SwapAsset> =
        withContext(Dispatchers.Default) {
            nearApiProvider
                .getSupportedTokens()
                .distinctBy { Triple(it.symbol, it.blockchain, it.decimals) }
                .map { buildSwapAsset(it) }
        }

    private fun buildSwapAsset(dto: NearTokenDto): SwapAsset =
        NearSwapAsset(
            tokenTicker = dto.symbol,
            tokenName = tokenNameProvider.getName(dto.symbol),
            tokenIcon = tokenIconProvider.getIcon(dto.symbol),
            usdPrice = dto.price,
            assetId = dto.assetId,
            decimals = dto.decimals,
            blockchain = blockchainProvider.getBlockchain(dto.blockchain),
        )

    @Suppress("MagicNumber", "CyclomaticComplexMethod")
    override suspend fun requestQuote(
        swapMode: SwapMode,
        amount: BigDecimal,
        refundAddress: String,
        originAsset: SwapAsset,
        destinationAddress: String,
        destinationAsset: SwapAsset,
        slippage: BigDecimal,
        affiliateAddress: String
    ): SwapQuote {
        val decimals =
            when (swapMode) {
                SwapMode.EXACT_INPUT, SwapMode.FLEX_INPUT -> originAsset.decimals
                SwapMode.EXACT_OUTPUT -> destinationAsset.decimals
            }

        val shifted = amount.movePointRight(decimals)
        val integer = shifted.toBigInteger().toBigDecimal()
        val normalizedAmount = shifted.round(MathContext(integer.precision(), RoundingMode.DOWN))

        val slippageToleranceBps = slippage.multiply(BigDecimal(100), MathContext.DECIMAL128).toInt()

        val request =
            QuoteRequest(
                dry = false,
                swapType =
                    when (swapMode) {
                        SwapMode.EXACT_INPUT -> SwapType.EXACT_INPUT
                        SwapMode.FLEX_INPUT -> SwapType.FLEX_INPUT
                        SwapMode.EXACT_OUTPUT -> SwapType.EXACT_OUTPUT
                    },
                slippageTolerance = slippageToleranceBps,
                originAsset = originAsset.assetId,
                depositType = RefundType.ORIGIN_CHAIN,
                destinationAsset = destinationAsset.assetId,
                amount = normalizedAmount,
                refundTo = refundAddress,
                refundType = RefundType.ORIGIN_CHAIN,
                recipient = destinationAddress,
                recipientType = RecipientType.DESTINATION_CHAIN,
                deadline = Clock.System.now() + 2.hours,
                quoteWaitingTimeMs = QUOTE_WAITING_TIME,
                appFees =
                    listOf(
                        AppFee(
                            recipient = affiliateAddress,
                            fee = AFFILIATE_FEE_BPS
                        )
                    ),
                referral = "zodl"
            )

        return try {
            val response = nearApiProvider.requestQuote(request)
            require(response.quoteRequest.swapType == request.swapType) {
                "Swap quote type mismatch: requested ${request.swapType} " +
                    "but server returned ${response.quoteRequest.swapType}"
            }
            NearSwapQuote(
                response = response,
                originAsset = originAsset,
                destinationAsset = destinationAsset,
                depositAddress = getDepositAddress(response, originAsset),
                destinationAddress = getDestinationAddress(response, originAsset),
                refundAddress = getRefundAddress(response, originAsset),
                expectedSlippageToleranceBps = slippageToleranceBps,
            )
        } catch (e: SwapAmountInconsistencyException) {
            // MOB-1371 monitoring signal: the exact-equality amount-consistency check rejected this quote.
            // Report a sanitized non-fatal (field + decimals only, never the amounts — see the release
            // log-redaction hardening) so that a future 1Click change to rounded display values surfaces as
            // an observable "quotes blocked" signal instead of silent breakage. Keep failing closed: rethrow
            // so the quote is still rejected.
            GlobalCrashReporter.reportCaughtException(
                SwapAmountConsistencyRejectedSignal(field = e.field, decimals = e.decimals)
            )
            throw e
        } catch (e: ResponseWithNearErrorException) {
            when {
                e.error.message.contains("Amount is too low for bridge, try at least", true) -> {
                    val errorAmount =
                        e.error.message
                            .split(" ")
                            .lastOrNull()
                            ?.toBigDecimalOrNull() ?: throw e
                    val errorAsset =
                        when (swapMode) {
                            SwapMode.FLEX_INPUT -> originAsset
                            SwapMode.EXACT_INPUT -> originAsset
                            SwapMode.EXACT_OUTPUT -> destinationAsset
                        }
                    throw QuoteLowAmountException(
                        asset = errorAsset,
                        amount = errorAmount,
                        amountFormatted = errorAmount.movePointLeft(errorAsset.decimals)
                    )
                }

                e.error.message.contains("No quotes found", true) -> {
                    throw QuoteLowAmountException(
                        asset = originAsset,
                        amount = null,
                        amountFormatted = null
                    )
                }

                else -> {
                    throw e
                }
            }
        }
    }

    override suspend fun submitDepositTransaction(txHash: String, depositAddress: String) {
        nearApiProvider.submitDepositTransaction(
            SubmitDepositTransactionRequest(
                txHash = txHash,
                depositAddress = depositAddress
            )
        )
    }

    override suspend fun checkSwapStatus(depositAddress: String, supportedTokens: List<SwapAsset>): SwapQuoteStatus {
        val response = this.nearApiProvider.checkSwapStatus(depositAddress)
        val originAsset =
            supportedTokens.find { it.assetId == response.quoteResponse.quoteRequest.originAsset }
                ?: throw AssetNotFoundException(response.quoteResponse.quoteRequest.originAsset)
        val destinationAsset =
            supportedTokens.find { it.assetId == response.quoteResponse.quoteRequest.destinationAsset }
                ?: throw AssetNotFoundException(response.quoteResponse.quoteRequest.destinationAsset)
        log("checkSwapStatus $depositAddress")
        return NearSwapQuoteStatus(
            response = response,
            origin = originAsset,
            destination = destinationAsset,
            depositAddress = getDepositAddress(response.quoteResponse, originAsset),
            destinationAddress = getDestinationAddress(response.quoteResponse, originAsset),
            refundAddress = getRefundAddress(response.quoteResponse, originAsset),
        )
    }

    private suspend fun getDepositAddress(response: QuoteResponseDto, originAsset: SwapAsset): SwapAddress {
        val address = response.quote.depositAddress
        return if (originAsset.isZCashAsset) getZcashSwapAddress(address) else DynamicSwapAddress(address)
    }

    private suspend fun getDestinationAddress(response: QuoteResponseDto, originAsset: SwapAsset): SwapAddress {
        val address = response.quoteRequest.recipient
        return if (originAsset.isZCashAsset) DynamicSwapAddress(address) else getZcashSwapAddress(address)
    }

    private suspend fun getRefundAddress(response: QuoteResponseDto, originAsset: SwapAsset): SwapAddress {
        val address = response.quoteRequest.refundTo
        return if (originAsset.isZCashAsset) getZcashSwapAddress(address) else DynamicSwapAddress(address)
    }

    private suspend fun getZcashSwapAddress(address: String): ZcashSwapAddress =
        when (synchronizerProvider.getSynchronizer().validateAddress(address)) {
            AddressType.Unified,
            AddressType.Shielded -> ZcashShieldedSwapAddress(address)

            AddressType.Tex,
            AddressType.Transparent -> ZcashTransparentSwapAddress(address)

            is AddressType.Invalid -> throw IllegalArgumentException("Zcash address is invalid")
        }
}

const val AFFILIATE_FEE_BPS = 67
const val AFFILIATE_ADDRESS = "d78abd5477432c9d9c5e32c4a1a0056cd7b8be6580d3c49e1f97185b786592db"
private const val QUOTE_WAITING_TIME = 3000

/**
 * Sanitized non-fatal reported to crash monitoring when the swap amount-consistency check rejects a quote
 * (MOB-1371). Carries only the field name and decimal precision — never the amounts — so it does not leak
 * transaction values to crash reporting.
 */
private class SwapAmountConsistencyRejectedSignal(
    field: String,
    decimals: Int
) : Exception("Swap amount-consistency check rejected a quote (field=$field, decimals=$decimals)")
