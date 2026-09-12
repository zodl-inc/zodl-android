package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpHttpClient
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.near.ErrorDto
import co.electriccoin.zcash.ui.common.model.near.NearTokenDto
import co.electriccoin.zcash.ui.common.model.near.QuoteRequest
import co.electriccoin.zcash.ui.common.model.near.QuoteResponseDto
import co.electriccoin.zcash.ui.common.model.near.SubmitDepositTransactionRequest
import co.electriccoin.zcash.ui.common.model.near.SwapStatusResponseDto
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
import io.ktor.serialization.kotlinx.json.DefaultJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

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
            ohttpHeaders = JSON_HEADERS,
            ohttpBodyJson = Json.encodeToString(QuoteRequest.serializer(), request),
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
            ohttpHeaders = JSON_HEADERS,
            ohttpBodyJson = Json.encodeToString(SubmitDepositTransactionRequest.serializer(), request),
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
            // canHandle() only checks the host has a proxy mapping; the Tor preference decides the path.
            if (!httpClientProvider.isTorEnabled() && ohttpClient.canHandle(ohttpUrl)) {
                // Tor OFF path: route through OHTTP gateway
                val response =
                    try {
                        ohttpClient.execute(
                            OhttpHttpClient.Request(
                                url = ohttpUrl,
                                method = ohttpMethod,
                                headers = ohttpHeaders,
                                body = ohttpBodyJson?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                            )
                        )
                    } catch (e: Exception) {
                        Twig.warn(e) { "OHTTP $ohttpMethod $ohttpUrl failed" }
                        throw e
                    }
                Twig.debug { "OHTTP $ohttpMethod $ohttpUrl -> HTTP ${response.status} (${response.body.size} bytes)" }
                if (response.status !in HTTP_SUCCESS) {
                    error("OHTTP proxy returned HTTP ${response.status} for $ohttpUrl")
                }
                if (T::class == Unit::class) {
                    @Suppress("UNCHECKED_CAST")
                    return@withContext Unit as T
                }
                try {
                    OHTTP_JSON.decodeFromString<T>(response.body.decodeToString())
                } catch (e: Exception) {
                    Twig.warn(e) { "OHTTP $ohttpMethod $ohttpUrl decode failed" }
                    throw e
                }
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

/** HTTP status range the OHTTP proxy path treats as success. */
private val HTTP_SUCCESS = 200..299

/**
 * Same lenient defaults as Ktor's `ContentNegotiation { json() }` used on the Tor path. The NEAR
 * DTOs decode numbers through [co.electriccoin.zcash.ui.common.serialization.BigDecimalSerializer]
 * (`decodeString()`), which only works with `isLenient = true`; a strict `Json {}` fails on
 * `"price":2.37` and the swap picker shows "We couldn't load the assets".
 */
private val OHTTP_JSON = Json(from = DefaultJson) { ignoreUnknownKeys = true }

private val JSON_HEADERS = listOf("Authorization" to AUTH_TOKEN, "Content-Type" to "application/json")

private const val AUTH_TOKEN =
    "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IjIwMjUtMDEtMTItdjEifQ.eyJ2IjoxLCJrZXlfdHlwZSI6ImRpc3RyaWJ1dG" +
        "lvbl9jaGFubmVsIiwicGFydG5lcl9pZCI6InpvZGwiLCJpYXQiOjE3NzUwMjc5MzgsImV4cCI6MTgwNjU2MzkzOH0.TwBxHbVpCZWhvREfK0" +
        "hvQMPn4eibIqBylc9AI-dy7xR5-k519lFoDxqTaIfv70E-Oa4aswOkWv_Rx2OQUkbL41RMHxWiOU6jnn6z6gXVGfylZ-sZK5bELnvPugyucD" +
        "-BLSRw3ia-czvTO_LDYGBNDO78J0QbjOe0-CIbg06PxA59tui8SGXQ5paGi6bHUpdjnKXOOB1PAwkIZOaZjqrlh4YoATdFhAgEfbfu9zXdNq" +
        "jtmYfmAzn8B3_hGI_ZoyIN1sR3ka-LWa5rIaIMfaqBTvDFn2zmCdCbUCUMKhkeBijfpVAf3UmrK4gQeLgo70AQqAFSHZeIk6X0loH_AwzBag"
