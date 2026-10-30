package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import io.ktor.client.HttpClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FlexaOhttpTransportImplTest {
    private var isolatedCalls = 0
    private var sharedCalls = 0

    private val response = OhttpHttpClient.Response(status = 200, body = ByteArray(0))
    private val client =
        mockk<OhttpHttpClient> {
            coEvery { execute(any(), any()) } returns response
            coEvery { executeStreaming(any(), any(), any()) } returns Unit
        }

    private val provider =
        object : HttpClientProvider {
            override suspend fun create(): HttpClient = error("Flexa must not go direct")

            override suspend fun createTor(): HttpClient = error("Flexa must not go Tor-direct")

            override suspend fun create(tor: Boolean): HttpClient =
                if (tor) createTor() else create()

            override suspend fun createOhttp(): OhttpHttpClient {
                sharedCalls++
                return client
            }

            override suspend fun <T> withIsolatedOhttp(block: suspend (OhttpHttpClient) -> T): T {
                isolatedCalls++
                return block(client)
            }

            override suspend fun isTorEnabled(): Boolean = true
        }

    @Test
    fun `every Flexa call gets an OHTTP client of its own`() =
        runTest {
            val transport = FlexaOhttpTransportImpl(provider)
            val request = mockk<OhttpHttpClient.Request>()

            transport.execute(request, 1_000)
            transport.execute(request, 1_000)
            transport.executeStreaming(request, 1_000, mockk())

            assertEquals(3, isolatedCalls)
            assertEquals(0, sharedCalls)
        }
}
