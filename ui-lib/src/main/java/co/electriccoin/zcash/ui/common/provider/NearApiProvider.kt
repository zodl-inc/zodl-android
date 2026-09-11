package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.near.ErrorDto
import co.electriccoin.zcash.ui.common.model.near.NearTokenDto
import co.electriccoin.zcash.ui.common.model.near.QuoteRequest
import co.electriccoin.zcash.ui.common.model.near.QuoteResponseDto
import co.electriccoin.zcash.ui.common.model.near.SubmitDepositTransactionRequest
import co.electriccoin.zcash.ui.common.model.near.SwapStatusResponseDto
import cash.z.ecc.android.sdk.lightwallet.client.OhttpHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias GetNearSupportedTokensResponse = List<NearTokenDto>

interface NearApiProvider {
    @Throws(ResponseException::class, ResponseWithNearErrorException::class)
    suspend fun getSupportedTokens(): GetNearSupportedTokensResponse

    @Throws(ResponseException::class, ResponseWithNearErrorException::class)
    suspend fun requestQuote(request: QuoteRequest): QuoteResponseDto

    @Throws(ResponseException::class, ResponseWithNearErrorException::class)
    suspend fun submitDepositTransaction(request: SubmitDepositTransactionRequest)

    @Throws(ResponseException::class, ResponseWithNearErrorException::class)
    suspend fun checkSwapStatus(depositAddress: String): SwapStatusResponseDto
}

class ResponseWithNearErrorException(
    val error: ErrorDto,
    override val cause: ResponseException
) : ResponseException(
        response = cause.response,
        cachedResponseText = "Code: ${cause.response.status}, message: ${error.message}"
    )

class KtorNearApiProvider(
    private val httpClientProvider: HttpClientProvider
) : NearApiProvider {

    // Privacy routing:
    //   Tor OFF → OHTTP proxy (1click.chaindefuser.com → near.zodl.com via gateway)
    //   Tor ON  → Tor direct (httpClientProvider.create() returns Tor client)
    private val ohttpClient: OhttpHttpClient = httpClientProvider.createOhttp()

    override suspend fun getSupportedTokens(): GetNearSupportedTokensResponse =
        execute(
            torBlock = { get("https://1click.chaindefuser.com/v0/tokens").body() },
            ohttpUrl = "https://1click.chaindefuser.com/v0/tokens",
            ohttpMethod = "GET",
        )

    override suspend fun requestQuote(request: QuoteRequest): QuoteResponseDto =
        execute(
            torBlock = {
                post("https://1click.chaindefuser.com/v0/quote") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, AUTH_TOKEN)
                    setBody(request)
                }.body()
            },
            ohttpUrl = "https://1click.chaindefuser.com/v0/quote",
            ohttpMethod = "POST",
            ohttpHeaders = listOf("Authorization" to AUTH_TOKEN, "Content-Type" to "application/json"),
            ohttpBodyJson = kotlinx.serialization.json.Json.encodeToString(QuoteRequest.serializer(), request),
        )

    override suspend fun submitDepositTransaction(request: SubmitDepositTransactionRequest) {
        execute<Unit>(
            torBlock = {
                post("https://1click.chaindefuser.com/v0/deposit/submit") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, AUTH_TOKEN)
                    setBody(request)
                }.let { }
            },
            ohttpUrl = "https://1click.chaindefuser.com/v0/deposit/submit",
            ohttpMethod = "POST",
            ohttpHeaders = listOf("Authorization" to AUTH_TOKEN, "Content-Type" to "application/json"),
            ohttpBodyJson = kotlinx.serialization.json.Json.encodeToString(SubmitDepositTransactionRequest.serializer(), request),
        )
    }

    override suspend fun checkSwapStatus(depositAddress: String): SwapStatusResponseDto =
        execute(
            torBlock = {
                get("https://1click.chaindefuser.com/v0/status") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, AUTH_TOKEN)
                    parameter("depositAddress", depositAddress)
                }.body()
            },
            ohttpUrl = "https://1click.chaindefuser.com/v0/status?depositAddress=$depositAddress",
            ohttpMethod = "GET",
            ohttpHeaders = listOf("Authorization" to AUTH_TOKEN),
        )

    /**
     * Executes a request, routing through OHTTP when Tor is off and through Tor when Tor is on.
     *
     * Tor OFF: [ohttpClient] rewrites the hostname to `near.zodl.com` and sends encrypted via
     * relay→gateway. Chaindefuser only sees the gateway's IP.
     * Tor ON:  [torBlock] runs with a Tor-backed Ktor client. The Tor exit IP is seen by
     * Chaindefuser; no OHTTP wrapping (no double-hop needed).
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <reified T> execute(
        crossinline torBlock: suspend HttpClient.() -> T,
        ohttpUrl: String,
        ohttpMethod: String = "GET",
        ohttpHeaders: List<Pair<String, String>> = emptyList(),
        ohttpBodyJson: String? = null,
    ): T =
        withContext(Dispatchers.IO) {
            if (ohttpClient.canHandle(ohttpUrl)) {
                // Tor OFF path: route through OHTTP gateway
                val response = ohttpClient.execute(
                    OhttpHttpClient.Request(
                        url = ohttpUrl,
                        method = ohttpMethod,
                        headers = ohttpHeaders,
                        body = ohttpBodyJson?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                    )
                )
                if (response.status !in 200..299) {
                    throw IllegalStateException("OHTTP proxy returned HTTP ${response.status} for $ohttpUrl")
                }
                if (T::class == Unit::class) {
                    @Suppress("UNCHECKED_CAST")
                    return@withContext Unit as T
                }
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString<T>(response.body.decodeToString())
            } else {
                // Tor ON path: Tor direct (httpClientProvider.create() returns Tor client)
                httpClientProvider.create().use {
                    try {
                        torBlock(it)
                    } catch (e: ResponseException) {
                        val response = e.response
                        val error: ErrorDto? = runCatching { response.body<ErrorDto?>() }.getOrNull()
                        if (error != null) {
                            throw ResponseWithNearErrorException(error = error, cause = e)
                        } else {
                            throw e
                        }
                    }
                }
            }
        }
}

private const val AUTH_TOKEN =
    "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IjIwMjUtMDEtMTItdjEifQ.eyJ2IjoxLCJrZXlfdHlwZSI6ImRpc3RyaWJ1dG" +
        "lvbl9jaGFubmVsIiwicGFydG5lcl9pZCI6InpvZGwiLCJpYXQiOjE3NzUwMjc5MzgsImV4cCI6MTgwNjU2MzkzOH0.TwBxHbVpCZWhvREfK0" +
        "hvQMPn4eibIqBylc9AI-dy7xR5-k519lFoDxqTaIfv70E-Oa4aswOkWv_Rx2OQUkbL41RMHxWiOU6jnn6z6gXVGfylZ-sZK5bELnvPugyucD" +
        "-BLSRw3ia-czvTO_LDYGBNDO78J0QbjOe0-CIbg06PxA59tui8SGXQ5paGi6bHUpdjnKXOOB1PAwkIZOaZjqrlh4YoATdFhAgEfbfu9zXdNq" +
        "jtmYfmAzn8B3_hGI_ZoyIN1sR3ka-LWa5rIaIMfaqBTvDFn2zmCdCbUCUMKhkeBijfpVAf3UmrK4gQeLgo70AQqAFSHZeIk6X0loH_AwzBag"
