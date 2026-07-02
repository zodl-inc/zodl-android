package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.datasource.AFFILIATE_ADDRESS
import co.electriccoin.zcash.ui.common.datasource.AFFILIATE_FEE_BPS
import co.electriccoin.zcash.ui.common.model.near.AppFee
import co.electriccoin.zcash.ui.common.model.near.QuoteRequest
import co.electriccoin.zcash.ui.common.model.near.RecipientType
import co.electriccoin.zcash.ui.common.model.near.RefundType
import co.electriccoin.zcash.ui.common.model.near.SwapType
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * LIVE integration test against the real NEAR 1Click API. Skipped unless `ZCASH_RUN_LIVE_SWAP_API_TESTS=true`
 * (see [assumeLiveApiTestsEnabled]); never runs in CI. Quote amounts / deposit addresses vary, so assertions
 * only check the response shape — that the real call succeeds and deserializes — not exact values.
 */
class KtorNearApiProviderLiveTest {
    private val provider = KtorNearApiProvider(realHttpClientProvider())

    @Test
    fun getSupportedTokensReturnsZec() =
        runBlocking {
            assumeLiveApiTestsEnabled()

            val tokens = provider.getSupportedTokens()

            assertTrue(tokens.isNotEmpty(), "token list must not be empty")
            assertTrue(tokens.any { it.symbol.equals("ZEC", ignoreCase = true) }, "ZEC must be supported")
        }

    @Test
    fun requestQuoteZecToNear() =
        runBlocking {
            assumeLiveApiTestsEnabled()

            val tokens = provider.getSupportedTokens()
            val zec = tokens.first { it.symbol.equals("ZEC", ignoreCase = true) }
            val near =
                tokens.first {
                    it.symbol.equals("USDC", ignoreCase = true) && it.blockchain.equals("near", ignoreCase = true)
                }

            val response =
                provider.requestQuote(
                    QuoteRequest(
                        dry = false,
                        swapType = SwapType.EXACT_INPUT,
                        slippageTolerance = 100,
                        originAsset = zec.assetId,
                        depositType = RefundType.ORIGIN_CHAIN,
                        destinationAsset = near.assetId,
                        // 0.1 ZEC expressed in base units (zatoshi); the 1Click `amount` field is base units.
                        amount = BigDecimal("0.1").movePointRight(zec.decimals),
                        refundTo = UA_ADDRESS,
                        refundType = RefundType.ORIGIN_CHAIN,
                        recipient = NEAR_RECIPIENT,
                        recipientType = RecipientType.DESTINATION_CHAIN,
                        deadline = Clock.System.now() + 2.hours,
                        quoteWaitingTimeMs = 3000,
                        appFees = listOf(AppFee(recipient = AFFILIATE_ADDRESS, fee = AFFILIATE_FEE_BPS)),
                        referral = "zodl"
                    )
                )

            assertTrue(response.quote.depositAddress.isNotBlank(), "a deposit address must be returned")
            assertTrue(response.quote.amountInFormatted.signum() > 0, "amountInFormatted must be positive")
            assertTrue(response.quote.amountOutFormatted.signum() > 0, "amountOutFormatted must be positive")
        }
}
