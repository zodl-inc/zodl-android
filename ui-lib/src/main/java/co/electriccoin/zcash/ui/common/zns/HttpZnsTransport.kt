package co.electriccoin.zcash.ui.common.zns

import co.electriccoin.zcash.ui.common.provider.HttpClientProvider
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Sends ZNS requests through the app's own HTTP clients: over Tor when the user turned Tor on (failing while Tor
 * is unavailable, never falling back to a direct connection), otherwise directly. The ZNS hosts are not mapped by
 * the OHTTP gateway, so OHTTP is not an option here.
 *
 * Redirects are refused, so a resolver cannot hand the lookup to another host.
 */
class HttpZnsTransport(
    private val httpClientProvider: HttpClientProvider,
    private val timeout: Duration = 30.seconds,
) : ZnsTransport {
    override suspend fun post(
        url: String,
        body: String
    ): String =
        withContext(Dispatchers.IO) {
            withTimeout(timeout) {
                httpClientProvider.create().use { base ->
                    base.config { followRedirects = false }.use { client ->
                        val response =
                            client.post(url) {
                                accept(ContentType.Application.Json)
                                setBody(TextContent(body, ContentType.Application.Json))
                            }
                        val bytes = response.bodyAsChannel().readRemaining(MAX_REPLY_BYTES + 1).readByteArray()
                        if (bytes.size > MAX_REPLY_BYTES) throw ZnsHostUnavailableException("reply is too large")
                        bytes.decodeToString()
                    }
                }
            }
        }

    private companion object {
        /** A full page of 500 records is about 180 KB of JSON. */
        const val MAX_REPLY_BYTES = 1024L * 1024L
    }
}
