package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

/**
 * `OhttpHttpClient` is a final class with a private constructor (only buildable via its own
 * `create(codec, sender, ...)` factory, which needs the native `OhttpCodec` and a real gateway) --
 * it can't be subclassed, and a JVM test cannot load the native codec. Tests that don't care about
 * OHTTP routing itself
 * (everything pre-dating the OHTTP feature) use this instead: a MockK mock that always reports
 * [OhttpHttpClient.canHandle] = true and forwards [OhttpHttpClient.execute] to the same MockEngine
 * client the test already built via `create()`/`createTor()`, so existing request-path/count
 * assertions keep working unchanged. Tests that specifically verify the OHTTP-vs-direct routing
 * DECISION (e.g. [VotingApiProviderOhttpRoutingTest]) build their own mock instead, with
 * `canHandle` reflecting the scenario under test.
 */
internal fun passthroughOhttpClient(realClient: suspend () -> HttpClient): OhttpHttpClient =
    mockk {
        every { canHandle(any()) } returns true
        coEvery { execute(any()) } coAnswers {
            val request = firstArg<OhttpHttpClient.Request>()
            val response =
                realClient().request(request.url) {
                    // The real OHTTP client reports non-2xx as a Response, never as a thrown
                    // ResponseException, so opt out of the wrapped client's expectSuccess here.
                    expectSuccess = false
                    method = HttpMethod.parse(request.method)
                    request.headers.forEach { (name, value) -> header(name, value) }
                    if (request.body.isNotEmpty()) setBody(request.body)
                }
            OhttpHttpClient.Response(status = response.status.value, body = response.bodyAsBytes())
        }
    }
