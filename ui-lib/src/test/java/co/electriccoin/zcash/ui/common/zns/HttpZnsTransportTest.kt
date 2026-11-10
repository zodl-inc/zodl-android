package co.electriccoin.zcash.ui.common.zns

import co.electriccoin.zcash.ui.common.provider.HttpClientProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpZnsTransportTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun provider(engine: MockEngine) =
        mockk<HttpClientProvider> {
            coEvery { create() } answers { HttpClient(engine) }
        }

    @Test
    fun aRequestIsAJsonPostThroughTheUsersRoute() =
        runTest {
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(
                        content = """{"ok":true}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            val provider = provider(engine)

            val reply = HttpZnsTransport(provider).post("https://resolver.example", """{"method":"status"}""")

            assertEquals("""{"ok":true}""", reply)
            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("""{"method":"status"}""", (request.body as TextContent).text)
            assertTrue(request.headers[HttpHeaders.Accept].orEmpty().contains("application/json"))
            // create() picks Tor or direct from the user's Tor setting, and never OHTTP.
            coVerify(exactly = 1) { provider.create() }
        }

    @Test
    fun aRedirectIsNotFollowed() =
        runTest {
            val engine =
                MockEngine { request ->
                    requests += request
                    if (request.url.host == "resolver.example") {
                        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://elsewhere.example/"))
                    } else {
                        respond("""{"jsonrpc":"2.0","id":1,"result":{}}""", HttpStatusCode.OK)
                    }
                }

            HttpZnsTransport(provider(engine)).post("https://resolver.example", "{}")

            assertEquals(listOf("resolver.example"), requests.map { it.url.host })
        }

    @Test
    fun anOversizedReplyIsRefused() =
        runTest {
            val engine = MockEngine { respond("x".repeat(1024 * 1024 + 1), HttpStatusCode.OK) }

            assertFailsWith<ZnsHostUnavailableException> {
                HttpZnsTransport(provider(engine)).post("https://resolver.example", "{}")
            }
        }
}
