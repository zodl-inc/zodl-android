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

/**
 * Thrown when the OHTTP path receives a non-2xx response with a parsable NEAR error body.
 * Callers can catch this to inspect [error.message] for specific NEAR error conditions
 * (e.g. "Amount is too low for bridge"). Analogous to [ResponseWithNearErrorException] but
 * does not depend on Ktor HttpResponse types (OHTTP responses are just status + bytes).
 */
class OhttpNearErrorException(
    val error: ErrorDto,
    val status: Int,
    val url: String
) : RuntimeException("HTTP $status from $url: ${error.message}")

class KtorNearApiProvider(
    private val httpClientProvider: HttpClientProvider
) : NearApiProvider {
    // Privacy routing (MOB-1520: secrets never leave app when Tor ON):
    //   Tor OFF → OHTTP gateway (relay sees app IP, gateway injects secret)
    //   Tor ON  → Tor→OHTTP gateway (relay sees Tor exit, gateway injects secret)
    // The app NEVER sends the secret itself — it's stripped with a warning if present (see
    // OhttpHttpClient.execute → stripSecretsForGateway).

    override suspend fun getSupportedTokens(): GetNearSupportedTokensResponse =
        execute(
            ohttpUrl = "https://1click.chaindefuser.com/v0/tokens",
            ohttpMethod = "GET"
        )

    override suspend fun requestQuote(request: QuoteRequest): QuoteResponseDto =
        execute(
            ohttpUrl = "https://1click.chaindefuser.com/v0/quote",
            ohttpMethod = "POST",
            ohttpHeaders = listOf("Content-Type" to "application/json"),
            ohttpBodyJson = Json.encodeToString(QuoteRequest.serializer(), request)
        )

    override suspend fun submitDepositTransaction(request: SubmitDepositTransactionRequest) {
        execute<Unit>(
            ohttpUrl = "https://1click.chaindefuser.com/v0/deposit/submit",
            ohttpMethod = "POST",
            ohttpHeaders = listOf("Content-Type" to "application/json"),
            ohttpBodyJson = Json.encodeToString(SubmitDepositTransactionRequest.serializer(), request)
        )
    }

    override suspend fun checkSwapStatus(depositAddress: String): SwapStatusResponseDto =
        execute(
            ohttpUrl = "https://1click.chaindefuser.com/v0/status?depositAddress=$depositAddress",
            ohttpMethod = "GET"
        )

    /**
     * Executes a NEAR API request through OHTTP. The gateway injects the Authorization token
     * server-side; the app no longer sends it (it's stripped if present). With Tor ON, the relay
     * leg goes over Tor; with Tor OFF, the relay sees the app's clearnet IP.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <reified T> execute(
        ohttpUrl: String,
        ohttpMethod: String = "GET",
        ohttpHeaders: List<Pair<String, String>> = emptyList(),
        ohttpBodyJson: String? = null
    ): T =
        withContext(Dispatchers.IO) {
            val ohttpClient = httpClientProvider.createOhttp()
            if (!ohttpClient.canHandle(ohttpUrl)) {
                error("No OHTTP proxy mapping for $ohttpUrl")
            }
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
                // Attempt to parse the NEAR error body for typed error handling downstream.
                val bodyString = response.body.decodeToString()
                val nearError =
                    try {
                        OHTTP_JSON.decodeFromString<ErrorDto>(bodyString)
                    } catch (ignore: Exception) {
                        null
                    }
                if (nearError != null) {
                    // Wrap in OhttpNearErrorException so callers can catch and inspect the NEAR error message.
                    throw OhttpNearErrorException(
                        error = nearError,
                        status = response.status,
                        url = ohttpUrl
                    )
                } else {
                    // Non-NEAR error body — throw generic exception with the raw body.
                    error("OHTTP $ohttpMethod $ohttpUrl returned HTTP ${response.status}: $bodyString")
                }
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
        }
}

private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299

/** HTTP status range the OHTTP proxy path treats as success. */
private val HTTP_SUCCESS = HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX

/**
 * Same lenient defaults as Ktor's `ContentNegotiation { json() }`. The NEAR DTOs decode numbers
 * through [co.electriccoin.zcash.ui.common.serialization.BigDecimalSerializer] (`decodeString()`),
 * which only works with `isLenient = true`; a strict `Json {}` fails on `"price":2.37`.
 */
private val OHTTP_JSON = Json(from = DefaultJson) { ignoreUnknownKeys = true }
