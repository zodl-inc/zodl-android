package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import io.ktor.client.HttpClient
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
 * `create(sender, ...)` factory, which performs a real HPKE/BinaryHttp round trip against a real
 * gateway) -- it can't be subclassed or given a fake [co.electriccoin.lightwallet.client.internal.OhttpSender]
 * without also faking the OHTTP wire protocol. Tests that don't care about OHTTP routing itself
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
                    method = HttpMethod.parse(request.method)
                    request.headers.forEach { (name, value) -> header(name, value) }
                    if (request.body.isNotEmpty()) setBody(request.body)
                }
            OhttpHttpClient.Response(status = response.status.value, body = response.bodyAsBytes())
        }
    }
