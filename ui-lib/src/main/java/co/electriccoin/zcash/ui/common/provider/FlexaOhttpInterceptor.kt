package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpException
import co.electriccoin.lightwallet.client.OhttpHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Pipe
import okio.Source
import okio.buffer
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Where [FlexaOhttpInterceptor] sends the Flexa API calls: the OHTTP client of [HttpClientProvider], whose
 * relay leg goes over Tor when the user turned Tor on.
 */
interface FlexaOhttpTransport {
    suspend fun execute(
        request: OhttpHttpClient.Request,
        timeoutMs: Int
    ): OhttpHttpClient.Response

    suspend fun executeStreaming(
        request: OhttpHttpClient.Request,
        timeoutMs: Int,
        listener: OhttpHttpClient.ResponseListener
    )
}

class FlexaOhttpTransportImpl(
    private val httpClientProvider: HttpClientProvider
) : FlexaOhttpTransport {
    override suspend fun execute(
        request: OhttpHttpClient.Request,
        timeoutMs: Int
    ) = httpClientProvider.createOhttp().execute(request, timeoutMs)

    override suspend fun executeStreaming(
        request: OhttpHttpClient.Request,
        timeoutMs: Int,
        listener: OhttpHttpClient.ResponseListener
    ) = httpClientProvider.createOhttp().executeStreaming(request, timeoutMs, listener)
}

/**
 * Sends the Flexa SDK's API calls to [FLEXA_OHTTP_HOST] through the OHTTP relay and the mainnet gateway,
 * which forwards them to [FLEXA_API_HOST]: Flexa sees the gateway's address, not the user's. Installed
 * after the SDK's own interceptors (FlexaClientConfiguration.okHttpClientCustomizer), so the request
 * already carries the SDK's Authorization, which the gateway swaps for the real publishable key when it
 * is built from [FLEXA_OHTTP_PUBLISHABLE_KEY]. It never proceeds down the chain: a request to another host
 * fails instead of leaving directly.
 *
 * A `text/event-stream` request (the commerce session events) is answered as its chunks arrive. The
 * gateway ends such a stream after at most 80 s; the end is reported as a failure, as a dropped connection
 * would be, so the SDK reconnects with its Last-Event-ID. A relay 429 is answered as a 429 with its
 * Retry-After; the SDK's OHTTP client refuses to send through that relay until it is over.
 */
class FlexaOhttpInterceptor(
    private val transport: FlexaOhttpTransport,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != FLEXA_API_HOST) {
            throw IOException("Only $FLEXA_API_HOST goes through OHTTP, not ${request.url.host}")
        }
        val inner = request.toOhttpRequest()
        return try {
            if (request.header("Accept").isEventStream()) {
                stream(chain.call(), request, inner)
            } else {
                exchange(request, inner)
            }
        } catch (e: OhttpException) {
            val retryAfter = e.retryAfterSeconds
            if (e.httpStatus != HTTP_TOO_MANY_REQUESTS || retryAfter == null) throw e
            response(request, HTTP_TOO_MANY_REQUESTS, listOf(RETRY_AFTER to retryAfter.toString()))
                .body(ByteArray(0).toResponseBody())
                .build()
        }
    }

    private fun exchange(
        request: Request,
        inner: OhttpHttpClient.Request
    ): Response {
        val answer = blocking { transport.execute(inner, OhttpHttpClient.DEFAULT_TIMEOUT_MS) }
        return response(request, answer.status, answer.headers)
            .body(answer.body.toResponseBody(answer.headers.contentType()))
            .build()
    }

    private fun stream(
        call: Call,
        request: Request,
        inner: OhttpHttpClient.Request
    ): Response {
        val head = CompletableDeferred<Pair<Int, List<Pair<String, String>>>>()
        var headFields: List<Pair<String, String>>? = null
        val pipe = Pipe(PIPE_BUFFER_BYTES)
        val out = pipe.sink.buffer()
        val body = StreamedBody(pipe.source)
        // async, not launch: a failure is handed to invokeOnCompletion instead of crashing the process.
        val job =
            scope.async {
                transport.executeStreaming(
                    inner,
                    STREAM_TIMEOUT_MS,
                    object : OhttpHttpClient.ResponseListener {
                        override fun onHead(
                            status: Int,
                            headers: List<Pair<String, String>>
                        ) {
                            headFields = headers
                            head.complete(status to headers)
                        }

                        override fun onData(bytes: ByteArray) {
                            out.write(bytes)
                            out.flush()
                        }

                        override fun onEnd() = Unit
                    }
                )
            }
        body.job = job
        job.invokeOnCompletion { cause ->
            head.completeExceptionally(cause ?: IOException("The OHTTP answer ended without a head"))
            body.endError =
                when {
                    cause != null -> cause.asIOException()
                    headFields.isEventStream() -> IOException("The event stream was ended by the OHTTP gateway")
                    else -> null
                }
            runCatching { out.close() }
        }
        scope.launch {
            while (job.isActive) {
                if (call.isCanceled()) {
                    job.cancel()
                    pipe.cancel()
                }
                delay(CANCEL_POLL_MS)
            }
        }
        val (status, headers) =
            try {
                blocking { head.await() }
            } catch (e: IOException) {
                job.cancel()
                throw e
            }
        return response(request, status, headers)
            .body(body.buffer().asResponseBody(headers.contentType(), -1L))
            .build()
    }

    /** The streamed body: at its end it throws what ended the stream, if anything; closing it stops the read. */
    private class StreamedBody(
        source: Source
    ) : ForwardingSource(source) {
        @Volatile
        var job: Job? = null

        @Volatile
        var endError: IOException? = null

        override fun read(
            sink: Buffer,
            byteCount: Long
        ): Long {
            val read = super.read(sink, byteCount)
            if (read == -1L) endError?.let { throw it }
            return read
        }

        override fun close() {
            job?.cancel()
            super.close()
        }
    }

    private fun response(
        request: Request,
        status: Int,
        headers: List<Pair<String, String>>
    ) = Response
        .Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(status)
        .message("")
        .headers(
            Headers
                .Builder()
                .apply { headers.forEach { (name, value) -> addUnsafeNonAscii(name, value) } }
                .build()
        )

    /** OkHttp only expects an IOException from an interceptor; anything else crashes an enqueued call. */
    @Suppress("TooGenericExceptionCaught")
    private fun <T> blocking(block: suspend () -> T): T =
        try {
            runBlocking { block() }
        } catch (e: InterruptedException) {
            throw InterruptedIOException("Interrupted").apply { initCause(e) }
        } catch (e: Exception) {
            throw e.asIOException()
        }
}

private fun Throwable.asIOException() = this as? IOException ?: IOException(message ?: javaClass.simpleName, this)

/** The Flexa API host the SDK calls, and the gateway alias its calls are sent to instead. */
internal const val FLEXA_API_HOST = "api.flexa.co"
internal const val FLEXA_OHTTP_HOST = "flexa.zodl.com"

/** The publishable key the app gives the Flexa SDK; the gateway replaces it with the real one. */
internal const val FLEXA_OHTTP_PUBLISHABLE_KEY = "publishable_live_zodl_gateway_placeholder"

private fun Request.toOhttpRequest(): OhttpHttpClient.Request {
    val content = body
    val bytes = content?.let { Buffer().also(it::writeTo).readByteArray() } ?: ByteArray(0)
    val fields = headers.filter { (name, _) -> name.lowercase() !in CONNECTION_FIELDS }
    val contentType =
        content
            ?.contentType()
            ?.takeIf { header(CONTENT_TYPE) == null }
            ?.let { listOf(CONTENT_TYPE to it.toString()) }
            .orEmpty()
    return OhttpHttpClient.Request(
        url =
            url
                .newBuilder()
                .host(FLEXA_OHTTP_HOST)
                .build()
                .toString(),
        method = method,
        headers = fields + contentType,
        body = bytes,
    )
}

private fun List<Pair<String, String>>.contentType(): MediaType? =
    firstOrNull { (name, _) -> name.equals(CONTENT_TYPE, ignoreCase = true) }?.second?.toMediaTypeOrNull()

private fun List<Pair<String, String>>?.isEventStream() = this?.contentType().isEventStream()

private fun String?.isEventStream() = this?.toMediaTypeOrNull().isEventStream()

private fun MediaType?.isEventStream() = this != null && type == "text" && subtype == "event-stream"

/** Fields of the outer hop that have no meaning in an inner request (RFC 9110 section 7.6.1, RFC 9113 8.2.2). */
private val CONNECTION_FIELDS =
    setOf("connection", "keep-alive", "proxy-connection", "transfer-encoding", "upgrade", "te", "host")

private const val CONTENT_TYPE = "Content-Type"
private const val RETRY_AFTER = "Retry-After"
private const val HTTP_TOO_MANY_REQUESTS = 429

/** Above the gateway's 80 s cap on a streamed answer, so the gateway ends it first. */
private const val STREAM_TIMEOUT_MS = 90_000
private const val PIPE_BUFFER_BYTES = 1024L * 1024L
private const val CANCEL_POLL_MS = 250L
