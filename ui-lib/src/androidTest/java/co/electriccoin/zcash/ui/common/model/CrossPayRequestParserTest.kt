package co.electriccoin.zcash.ui.common.model

import androidx.test.filters.SmallTest
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Exercises [CrossPayRequestParser.parse] end to end against real payment URI strings, unlike
 * [CrossPayRequestTest], which only covers [CrossPayRequest.resolveAsset] on already-constructed
 * requests. This needs the Rust-backed [cash.z.ecc.android.sdk.PaymentUriParser], so it must run
 * as an instrumentation test rather than a JVM unit test.
 */
class CrossPayRequestParserTest {
    @Test
    @SmallTest
    fun parsesBitcoinRequest() =
        runTest {
            val request =
                assertIs<CrossPayRequest>(
                    CrossPayRequestParser.parse(
                        "bitcoin:1FsSia9rv4NeEwvJ2GvXrX7LyxYspbN2mo?amount=20.3&label=Luke-Jr"
                    )
                )
            assertEquals("1FsSia9rv4NeEwvJ2GvXrX7LyxYspbN2mo", request.address)
            assertEquals(CrossPayRequest.AssetReference.Native("btc"), request.assetReference)
            assertEquals(BigDecimal("20.3"), request.amount?.value)
        }

    @Test
    @SmallTest
    fun parsesEthereumNativeRequestWithChainId() =
        runTest {
            val request =
                assertIs<CrossPayRequest>(
                    CrossPayRequestParser.parse(
                        "ethereum:0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359@42161?value=1e18"
                    )
                )
            assertEquals("0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359", request.address)
            assertEquals(
                CrossPayRequest.AssetReference.EvmNative(chainId = "42161"),
                request.assetReference
            )
        }

    @Test
    @SmallTest
    fun parsesEthereumErc20Request() =
        runTest {
            val contract = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
            val recipient = "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359"
            val request =
                assertIs<CrossPayRequest>(
                    CrossPayRequestParser.parse(
                        "ethereum:$contract@1/transfer?address=$recipient&uint256=1000000"
                    )
                )
            assertEquals(recipient, request.address)
            assertEquals(
                CrossPayRequest.AssetReference.Contract(chain = null, chainId = "1", address = contract),
                request.assetReference
            )
        }

    @Test
    @SmallTest
    fun parsesSolanaNativeTransfer() =
        runTest {
            val request =
                assertIs<CrossPayRequest>(
                    CrossPayRequestParser.parse(
                        "solana:mvines9iiHiQTysrwkJjGf2gb9Ex9jXJX8ns3qwf2kN?amount=1&label=Michael"
                    )
                )
            assertEquals("mvines9iiHiQTysrwkJjGf2gb9Ex9jXJX8ns3qwf2kN", request.address)
            assertEquals(CrossPayRequest.AssetReference.Native("sol"), request.assetReference)
        }

    @Test
    @SmallTest
    fun parsesSolanaSplTokenTransfer() =
        runTest {
            val mint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
            val request =
                assertIs<CrossPayRequest>(
                    CrossPayRequestParser.parse(
                        "solana:mvines9iiHiQTysrwkJjGf2gb9Ex9jXJX8ns3qwf2kN?amount=0.01&spl-token=$mint"
                    )
                )
            assertEquals(
                CrossPayRequest.AssetReference.Contract(chain = "sol", chainId = null, address = mint),
                request.assetReference
            )
        }

    @Test
    @SmallTest
    fun rejectsUnsupportedScheme() =
        runTest {
            assertNull(CrossPayRequestParser.parse("near:alice.near"))
        }

    @Test
    @SmallTest
    fun rejectsSolanaInteractiveTransactionLink() =
        runTest {
            assertNull(CrossPayRequestParser.parse("solana:https://example.com/solana-pay"))
        }

    @Test
    @SmallTest
    fun rejectsUnrecognisedEip681Method() =
        runTest {
            val contract = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
            val recipient = "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359"
            assertNull(CrossPayRequestParser.parse("ethereum:$contract/approve?address=$recipient"))
        }
}
