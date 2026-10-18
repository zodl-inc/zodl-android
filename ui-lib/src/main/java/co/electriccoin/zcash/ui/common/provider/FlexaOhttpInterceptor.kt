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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
 * fails instead of leaving directly. Cancelling the call, or its call timeout, ends the OHTTP exchange.
 *
 * A `text/event-stream` request (the commerce session events) is answered as its chunks arrive. The
 * gateway ends such a stream after at most 80 s; the end is reported as a failure, as a dropped connection
 * would be, so the SDK reconnects, and the reconnection carries as Last-Event-ID the id of the last event
 * the previous stream of the same Authorization delivered. A relay 429 is answered as a 429 with its
 * Retry-After; the SDK's OHTTP client refuses to send through that relay until it is over.
 */
class FlexaOhttpInterceptor(
    private val transport: FlexaOhttpTransport,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Interceptor {
    private val lastEventIds = ConcurrentHashMap<String, String>()

    /** OkHttp only expects an IOException from an interceptor; anything else crashes an enqueued call. */
    @Suppress("TooGenericExceptionCaught")
    override fun intercept(chain: Interceptor.Chain): Response =
        try {
            route(chain.call(), chain.request())
        } catch (e: OhttpException) {
            rateLimited(chain.request(), e) ?: throw e
        } catch (e: RuntimeException) {
            throw e.asIOException()
        }

    private fun route(
        call: Call,
        request: Request
    ): Response {
        if (request.url.host != FLEXA_API_HOST) {
            throw IOException("Only $FLEXA_API_HOST goes through OHTTP, not ${request.url.host}")
        }
        return if (request.header(ACCEPT).isEventStream()) stream(call, request) else exchange(call, request)
    }

    private fun rateLimited(
        request: Request,
        e: OhttpException
    ): Response? {
        val retryAfter = e.retryAfterSeconds
        if (e.httpStatus != HTTP_TOO_MANY_REQUESTS || retryAfter == null) return null
        return response(request, HTTP_TOO_MANY_REQUESTS, listOf(RETRY_AFTER to retryAfter.toString()))
            .body(ByteArray(0).toResponseBody())
            .build()
    }

    private fun exchange(
        call: Call,
        request: Request
    ): Response {
        val inner = request.toOhttpRequest()
        val timeoutMs = call.deadlineMs(OhttpHttpClient.DEFAULT_TIMEOUT_MS)
        val answer = scope.async { transport.execute(inner, timeoutMs) }
        cancelWhenCanceled(call, answer)
        val answered = blocking { answer.await() }
        return response(request, answered.status, answered.headers)
            .body(answered.body.toResponseBody(answered.headers.contentType()))
            .build()
    }

    private fun stream(
        call: Call,
        request: Request
    ): Response {
        val authorization = request.header(AUTHORIZATION)
        val resumeFrom = authorization?.let(lastEventIds::get)
        val inner =
            (resumeFrom?.let { request.newBuilder().header(LAST_EVENT_ID, it).build() } ?: request)
                .toOhttpRequest()
        val timeoutMs = call.deadlineMs(STREAM_TIMEOUT_MS)
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
                    timeoutMs,
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
        cancelWhenCanceled(call, job) { pipe.cancel() }
        val (status, headers) =
            try {
                blocking { head.await() }
            } catch (e: IOException) {
                job.cancel()
                throw e
            }
        if (authorization != null && headers.isEventStream()) {
            body.onEventId = { id ->
                if (id.isEmpty()) lastEventIds.remove(authorization) else lastEventIds[authorization] = id
            }
        }
        return response(request, status, headers)
            .body(body.buffer().asResponseBody(headers.contentType(), -1L))
            .build()
    }

    /** OkHttp has no cancellation callback for an interceptor, so the call is polled while [job] runs. */
    private fun cancelWhenCanceled(
        call: Call,
        job: Job,
        onCancel: () -> Unit = {}
    ) {
        scope.launch {
            while (job.isActive) {
                if (call.isCanceled()) {
                    job.cancel()
                    onCancel()
                }
                delay(CANCEL_POLL_MS)
            }
        }
    }

    /**
     * The streamed body: at its end it throws what ended the stream, if anything; closing it stops the
     * read. It hands each event `id` field it passes to [onEventId].
     */
    private class StreamedBody(
        source: Source
    ) : ForwardingSource(source) {
        @Volatile
        var job: Job? = null

        @Volatile
        var endError: IOException? = null

        @Volatile
        var onEventId: ((String) -> Unit)? = null

        private val line = Buffer()

        override fun read(
            sink: Buffer,
            byteCount: Long
        ): Long {
            val read = super.read(sink, byteCount)
            if (read == -1L) endError?.let { throw it }
            if (read > 0) onEventId?.let { scan(sink, read, it) }
            return read
        }

        private fun scan(
            sink: Buffer,
            read: Long,
            onEventId: (String) -> Unit
        ) {
            sink.copyTo(line, sink.size - read, read)
            while (true) {
                val end = line.indexOf(NEWLINE)
                if (end < 0) break
                val field = line.readUtf8(end).removeSuffix("\r")
                line.skip(1)
                eventId(field)?.let(onEventId)
            }
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

    /** Waits for [block] on the calling thread, which is OkHttp's: only IOExceptions leave it. */
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

/** The value of an SSE `id` field line (WHATWG HTML, server-sent events), or null for another line. */
internal fun eventId(line: String): String? {
    if (line != "id" && !line.startsWith("id:")) return null
    val value = line.removePrefix("id").removePrefix(":").removePrefix(" ")
    return value.takeUnless { '\u0000' in it }
}

/** [default] ms, or less if the call has a shorter call timeout of its own. */
private fun Call.deadlineMs(default: Int): Int {
    val callTimeoutMs = TimeUnit.NANOSECONDS.toMillis(timeout().timeoutNanos())
    return if (callTimeoutMs > 0) minOf(default.toLong(), callTimeoutMs).toInt() else default
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
    val nominated =
        headers(CONNECTION)
            .flatMap { it.split(',') }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
    val fields = headers.filter { (name, _) -> name.lowercase().let { it !in CONNECTION_FIELDS && it !in nominated } }
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

/**
 * Fields of the outer hop that have no meaning in an inner request, besides those the Connection field
 * names (RFC 9110 section 7.6.1, RFC 9113 section 8.2.2).
 */
private val CONNECTION_FIELDS =
    setOf("connection", "keep-alive", "proxy-connection", "transfer-encoding", "upgrade", "te", "host")

private const val ACCEPT = "Accept"
private const val AUTHORIZATION = "Authorization"
private const val CONNECTION = "Connection"
private const val CONTENT_TYPE = "Content-Type"
private const val LAST_EVENT_ID = "Last-Event-ID"
private const val RETRY_AFTER = "Retry-After"
private const val HTTP_TOO_MANY_REQUESTS = 429

/** Above the gateway's 80 s cap on a streamed answer, so the gateway ends it first. */
private const val STREAM_TIMEOUT_MS = 90_000
private const val PIPE_BUFFER_BYTES = 1024L * 1024L
private const val CANCEL_POLL_MS = 250L
private const val NEWLINE = '\n'.code.toByte()
