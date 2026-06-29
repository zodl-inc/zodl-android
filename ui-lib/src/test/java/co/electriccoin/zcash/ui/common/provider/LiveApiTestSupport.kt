package co.electriccoin.zcash.ui.common.provider

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import org.junit.Assume.assumeTrue

// Shared helpers for the LIVE swap-API integration tests (KtorNearApiProviderLiveTest,
// KtorSwapkitApiProviderLiveTest). These hit the real NEAR 1Click and SwapKit/Maya APIs over the network, so
// they are skipped unless ZCASH_RUN_LIVE_SWAP_API_TESTS=true is set — they must never run in CI. Run them
// manually with, e.g.:
//   ZCASH_RUN_LIVE_SWAP_API_TESTS=true ./gradlew :ui-lib:testZcashmainnetStoreDebugUnitTest \
//       --tests "co.electriccoin.zcash.ui.common.provider.KtorSwapkitApiProviderLiveTest"
//
// Hardcoded Zcash test addresses. The UA is broken across lines only to satisfy the line-length limit.
internal const val UA_ADDRESS =
    "u18hpvuaw6thrqm7n7jnzcjp5jrwmpnnmfellk5tysm9xwayfywdn9jk3a29mh4hkk5ky" +
        "k24f23slg7xsftm9ckmsszhs7s6gjrcqdgrre3whzc2ak6572drqqzglf9qy6wsdhj73h" +
        "gu3gj3hy93f5q52083hgeka0q4tthr6l9usn78hx"

internal const val T_ADDRESS = "t1RNB28HgrbvcuVxH1rsA2oB82MeqUHvG56"

internal const val NEAR_RECIPIENT = "test.near"

// BTC payout for the Maya buildSwap test (the buy side). The default is a well-known example address that
// SwapKit's screening will likely reject as a "dummy" — override it with a real BTC address you control via
// the ZCASH_LIVE_BTC_ADDRESS env var. With disableBuildTx no funds ever move.
internal val BTC_ADDRESS: String =
    System.getenv("ZCASH_LIVE_BTC_ADDRESS") ?: "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"

/** Skips the test unless `ZCASH_RUN_LIVE_SWAP_API_TESTS=true` — keeps live network calls out of CI. */
internal fun assumeLiveApiTestsEnabled() = assumeTrue(System.getenv("ZCASH_RUN_LIVE_SWAP_API_TESTS") == "true")

/** A real network-backed [HttpClientProvider] (clearnet, no Tor) for the live integration tests. */
@Suppress("MagicNumber")
internal fun realHttpClientProvider(): HttpClientProvider =
    object : HttpClientProvider {
        override suspend fun create(): HttpClient =
            HttpClient(OkHttp) {
                install(ContentNegotiation) { json() }
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 30_000
                    socketTimeoutMillis = 60_000
                }
                // Print full request/response (incl. bodies) so live runs show exactly what hits the wire.
                install(Logging) {
                    logger =
                        object : Logger {
                            override fun log(message: String) = println(message)
                        }
                    level = LogLevel.ALL
                }
                expectSuccess = true
            }

        override suspend fun createTor(): HttpClient = create()
    }
