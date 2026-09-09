package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitErrorDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitPriceDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitPriceRequestDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitPriceTokenRefDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitQuoteRequestDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitQuoteResponseDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitSwapRequestDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitSwapResponseDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitTokensResponseDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitTrackRequestDto
import co.electriccoin.zcash.ui.common.model.swapkit.SwapkitTrackResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SwapkitApiProvider {
    @Throws(ResponseException::class, ResponseWithSwapkitErrorException::class)
    suspend fun getSupportedTokens(providerNamespace: String): SwapkitTokensResponseDto

    @Throws(ResponseException::class, ResponseWithSwapkitErrorException::class)
    suspend fun getPrices(identifiers: List<String>): List<SwapkitPriceDto>

    @Throws(ResponseException::class, ResponseWithSwapkitErrorException::class)
    suspend fun requestQuote(request: SwapkitQuoteRequestDto): SwapkitQuoteResponseDto

    @Throws(ResponseException::class, ResponseWithSwapkitErrorException::class)
    suspend fun buildSwap(request: SwapkitSwapRequestDto): SwapkitSwapResponseDto

    @Throws(ResponseException::class, ResponseWithSwapkitErrorException::class)
    suspend fun track(request: SwapkitTrackRequestDto): SwapkitTrackResponseDto
}

class ResponseWithSwapkitErrorException(
    val error: SwapkitErrorDto,
    override val cause: ResponseException
) : ResponseException(
        response = cause.response,
        cachedResponseText = "Code: ${cause.response.status}, message: ${error.error ?: error.message}"
    )

class KtorSwapkitApiProvider(
    private val httpClientProvider: HttpClientProvider
) : SwapkitApiProvider {
    override suspend fun getSupportedTokens(providerNamespace: String): SwapkitTokensResponseDto =
        execute {
            get("$BASE_URL/tokens") {
                header(API_KEY_HEADER, API_KEY)
                parameter("provider", providerNamespace)
            }.body()
        }

    override suspend fun getPrices(identifiers: List<String>): List<SwapkitPriceDto> =
        execute {
            post("$BASE_URL/price") {
                contentType(ContentType.Application.Json)
                header(API_KEY_HEADER, API_KEY)
                setBody(
                    SwapkitPriceRequestDto(
                        tokens = identifiers.map { SwapkitPriceTokenRefDto(identifier = it) },
                        metadata = false
                    )
                )
            }.body()
        }

    override suspend fun requestQuote(request: SwapkitQuoteRequestDto): SwapkitQuoteResponseDto =
        execute {
            post("$BASE_URL/v3/quote") {
                contentType(ContentType.Application.Json)
                header(API_KEY_HEADER, API_KEY)
                setBody(request)
            }.body()
        }

    override suspend fun buildSwap(request: SwapkitSwapRequestDto): SwapkitSwapResponseDto =
        execute {
            post("$BASE_URL/v3/swap") {
                contentType(ContentType.Application.Json)
                header(API_KEY_HEADER, API_KEY)
                setBody(request)
            }.body()
        }

    override suspend fun track(request: SwapkitTrackRequestDto): SwapkitTrackResponseDto =
        execute {
            post("$BASE_URL/track") {
                contentType(ContentType.Application.Json)
                header(API_KEY_HEADER, API_KEY)
                setBody(request)
            }.body()
        }

    @Suppress("TooGenericExceptionCaught")
    @Throws(ResponseException::class)
    private suspend inline fun <T> execute(
        crossinline block: suspend HttpClient.() -> T
    ): T =
        withContext(Dispatchers.IO) {
            httpClientProvider.create().use {
                try {
                    block(it)
                } catch (e: ResponseException) {
                    val response = e.response
                    val error: SwapkitErrorDto? = runCatching { response.body<SwapkitErrorDto?>() }.getOrNull()
                    if (error != null && (error.error != null || error.message != null)) {
                        throw ResponseWithSwapkitErrorException(error = error, cause = e)
                    } else {
                        throw e
                    }
                }
            }
        }
}

private const val BASE_URL = "https://api.swapkit.dev"
private const val API_KEY_HEADER = "x-api-key"
private const val API_KEY = "4af1a3fb-1e3e-48e9-a96f-d7a0e4ac9f67"
