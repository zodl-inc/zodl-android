package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpException
import co.electriccoin.lightwallet.client.OhttpHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Flexa SDK's calls reach the gateway as inner requests to flexa.zodl.com, with the SDK's own
 * credentials, and never leave directly; event streams are handed over as they arrive.
 */
class FlexaOhttpInterceptorTest {
    private val sdkHeaders =
        Interceptor { chain ->
            chain.proceed(
                chain
                    .request()
                    .newBuilder()
                    .header("Authorization", AUTHORIZATION)
                    .header("Connection", "keep-alive, X-Hop")
                    .header("X-Hop", "1")
                    .build()
            )
        }

    private fun client(
        transport: FlexaOhttpTransport,
        configure: OkHttpClient.Builder.() -> Unit = {}
    ) = OkHttpClient
        .Builder()
        .addInterceptor(sdkHeaders)
        .addInterceptor(FlexaOhttpInterceptor(transport))
        .apply(configure)
        .build()

    @Test
    fun aRestCallIsSentToTheGatewayAliasWithTheSdkCredentials() {
        val transport =
            FakeTransport(
                answer = {
                    OhttpHttpClient.Response(
                        status = 201,
                        body = """{"id":"tok_1","status":"requires_verification"}""".toByteArray(),
                        headers = listOf("content-type" to "application/vnd.flexa+json", "date" to DATE)
                    )
                }
            )

        val response =
            client(transport)
                .newCall(
                    Request
                        .Builder()
                        .url("https://api.flexa.co/tokens?x=a,b")
                        .post("""{"email":"a@b.c"}""".toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                ).execute()

        val sent = transport.requests.single()
        assertEquals("https://flexa.zodl.com/tokens?x=a,b", sent.url)
        assertEquals("POST", sent.method)
        assertEquals("""{"email":"a@b.c"}""", sent.body.decodeToString())
        assertTrue(sent.headers.contains("Authorization" to AUTHORIZATION))
        assertTrue(sent.headers.contains("Content-Type" to "application/json; charset=utf-8"))
        assertTrue(
            sent.headers.none { (name, _) -> name.equals("Connection", true) || name.equals("X-Hop", true) },
            "connection fields, and the fields Connection names, stay on the outer hop"
        )
        assertEquals(OhttpHttpClient.DEFAULT_TIMEOUT_MS, transport.timeouts.single())
        assertEquals(201, response.code)
        assertEquals(DATE, response.header("date"))
        assertEquals("application/vnd.flexa+json", response.body.contentType().toString())
        assertEquals("""{"id":"tok_1","status":"requires_verification"}""", response.body.string())
    }

    @Test
    fun aContentTypeTheConnectionFieldNamesIsNotAddedFromTheBody() {
        val transport = FakeTransport()
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header("Connection", "Content-Type")
                            .build()
                    )
                }.addInterceptor(FlexaOhttpInterceptor(transport))
                .build()

        client
            .newCall(
                Request
                    .Builder()
                    .url("https://api.flexa.co/tokens")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            .close()

        val sent = transport.requests.single()
        assertTrue(sent.headers.none { (name, _) -> name.equals("Content-Type", true) })
    }

    @Test
    fun anotherHostIsRefusedAndNothingIsSent() {
        val transport = FakeTransport()

        assertFailsWith<IOException> {
            client(transport).newCall(Request.Builder().url("https://flexa.network/directory").build()).execute()
        }
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun aRelayRateLimitIsAnswered429WithItsRetryAfter() {
        val transport = FakeTransport(answer = { throw OhttpException("wait", 429, retryAfterSeconds = 7) })

        val response = client(transport).newCall(accountRequest()).execute()

        assertEquals(429, response.code)
        assertEquals("7", response.header("Retry-After"))
    }

    @Test
    fun anotherOhttpFailureFailsTheCall() {
        val transport = FakeTransport(answer = { throw OhttpException("relay answered 503", 503) })

        assertFailsWith<OhttpException> { client(transport).newCall(accountRequest()).execute() }
    }

    @Test
    fun aShorterCallTimeoutBoundsTheOhttpExchange() {
        val transport = FakeTransport()

        client(transport) { callTimeout(2, TimeUnit.SECONDS) }.newCall(accountRequest()).execute()

        assertTrue(transport.timeouts.single() in 1..2_000)
    }

    @Test
    fun cancellingARestCallEndsTheOhttpExchange() {
        val stopped = CompletableDeferred<Unit>()
        val started = CountDownLatch(1)
        val transport =
            FakeTransport(
                answer = {
                    started.countDown()
                    try {
                        awaitCancellation()
                    } finally {
                        stopped.complete(Unit)
                    }
                }
            )
        val call = client(transport).newCall(accountRequest())
        val failure = AtomicReference<Throwable>()
        val caller = thread { failure.set(runCatching { call.execute() }.exceptionOrNull()) }

        assertTrue(started.await(WAIT_S, TimeUnit.SECONDS))
        call.cancel()

        runBlocking { withTimeout(WAIT_S * MILLIS) { stopped.await() } }
        caller.join(WAIT_S * MILLIS)
        assertIs<IOException>(failure.get())
    }

    @Test
    fun cancellingReturnsTheCallAtOnceAndClosesTheConnectionOfABlockedSender() {
        val sockets = CopyOnWriteArrayList<FakeSocket>()
        val transport =
            FakeTransport(
                { listener ->
                    blockingRead(sockets)
                    listener.onEnd()
                },
                answer = {
                    blockingRead(sockets)
                    OhttpHttpClient.Response(200, ByteArray(0))
                }
            )
        val client = client(transport)

        listOf(accountRequest(), eventsRequest()).forEachIndexed { i, request ->
            val call = client.newCall(request)
            val failure = AtomicReference<Throwable>()
            val caller = thread { failure.set(runCatching { call.execute() }.exceptionOrNull()) }
            awaitSocket(sockets, i)

            call.cancel()

            caller.join(WAIT_S * MILLIS / 2)
            assertTrue(!caller.isAlive, "the call returned")
            assertIs<IOException>(failure.get())
            assertTrue(sockets[i].closed.await(WAIT_S, TimeUnit.SECONDS), "the sender's connection was closed")
        }
    }

    @Test
    fun aCallTimeoutThatFiresClosesTheConnectionOfABlockedSender() {
        val sockets = CopyOnWriteArrayList<FakeSocket>()
        val transport =
            FakeTransport(
                answer = {
                    blockingRead(sockets)
                    OhttpHttpClient.Response(200, ByteArray(0))
                }
            )
        val call =
            client(transport) { callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
                .newCall(accountRequest())
        val startedAt = System.nanoTime()

        assertFailsWith<IOException> { call.execute() }

        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) < WAIT_S * MILLIS / 2)
        assertTrue(transport.timeouts.single() <= CALL_TIMEOUT_MS)
        awaitSocket(sockets, 0)
        assertTrue(sockets[0].closed.await(WAIT_S, TimeUnit.SECONDS), "the sender's connection was closed")
    }

    @Test
    fun aStreamHeadTheResponseCannotCarryFailsTheCallAndEndsTheExchange() {
        val stopped = CompletableDeferred<Unit>()
        val transport =
            FakeTransport(
                { listener ->
                    listener.onHead(200, listOf("" to "x", "content-type" to "text/event-stream"))
                    try {
                        awaitCancellation()
                    } finally {
                        stopped.complete(Unit)
                    }
                }
            )

        assertFailsWith<IOException> { client(transport).newCall(eventsRequest()).execute() }
        runBlocking { withTimeout(WAIT_S * MILLIS) { stopped.await() } }
    }

    @Test
    fun aFailureOutsideTheExchangeFailsAnEnqueuedCallInsteadOfCrashing() {
        val transport = FakeTransport()
        val failure = CompletableDeferred<IOException>()
        val throwingBody =
            object : RequestBody() {
                override fun contentType(): MediaType? = null

                override fun writeTo(sink: BufferedSink): Unit = error("body")
            }

        client(transport)
            .newCall(
                Request
                    .Builder()
                    .url("https://api.flexa.co/tokens")
                    .post(throwingBody)
                    .build()
            ).enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException
                    ) {
                        failure.complete(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response
                    ) {
                        failure.completeExceptionally(AssertionError("unexpected response"))
                    }
                }
            )

        val e = runBlocking { withTimeout(WAIT_S * MILLIS) { failure.await() } }
        assertIs<IllegalStateException>(e.cause)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun anEventStreamIsReadAsItArrivesAndItsEndMakesTheSdkReconnect() {
        val release = CountDownLatch(1)
        val transport =
            FakeTransport(
                { listener ->
                    listener.onHead(200, listOf("content-type" to "text/event-stream"))
                    listener.onData("id: ev_2\nevent: commerce_session.created\ndata: {}\n\n".toByteArray())
                    release.await(WAIT_S, TimeUnit.SECONDS)
                    listener.onData("id: ev_3\ndata: {}\n\n".toByteArray())
                    listener.onEnd()
                }
            )

        val response = client(transport).newCall(eventsRequest()).execute()
        val source = response.body.source()

        assertEquals("text/event-stream", response.body.contentType().toString())
        assertEquals("id: ev_2", source.readUtf8LineStrict())
        assertEquals(1L, release.count, "the first event arrives before the stream ends")
        release.countDown()
        assertEquals("event: commerce_session.created", source.readUtf8LineStrict())
        assertEquals("data: {}", source.readUtf8LineStrict())
        assertEquals("", source.readUtf8LineStrict())
        assertEquals("id: ev_3", source.readUtf8LineStrict())
        assertEquals("data: {}", source.readUtf8LineStrict())
        assertEquals("", source.readUtf8LineStrict())
        assertFailsWith<IOException> { source.readUtf8Line() }

        val sent = transport.requests.single()
        assertEquals("https://flexa.zodl.com/events?type=commerce_session.created", sent.url)
        assertTrue(sent.headers.contains("Last-Event-ID" to "ev_1"))
        assertEquals(STREAM_TIMEOUT_MS, transport.timeouts.single())
    }

    @Test
    fun theReconnectionResumesAfterTheLastEventTheStreamDelivered() {
        val transport =
            FakeTransport(
                { listener ->
                    listener.onHead(200, listOf("content-type" to "text/event-stream"))
                    listener.onData("id: ev_2\ndata: {}\n\nid:ev_3\r\ndata: {}\r\n\r\n".toByteArray())
                    listener.onEnd()
                },
                { listener ->
                    listener.onHead(200, listOf("content-type" to "text/event-stream"))
                    listener.onEnd()
                }
            )
        val client = client(transport)

        val first = client.newCall(eventsRequest()).execute()
        assertFailsWith<IOException> { first.body.source().readByteArray() }
        client.newCall(eventsRequest()).execute().close()

        assertTrue(transport.requests[0].headers.contains("Last-Event-ID" to "ev_1"))
        assertTrue(transport.requests[1].headers.contains("Last-Event-ID" to "ev_3"))
    }

    @Test
    fun anEventCutBeforeItsEndIsNotResumedAfter() {
        val transport =
            FakeTransport(
                eventStream("id: ev_2\ndata: {}\n\nid: ev_3\ndata: {\"id\":"),
                eventStream()
            )
        val factory = EventSources.createFactory(client(transport))

        assertEquals(listOf("ev_2"), listenAsTheSdkDoes(factory))
        listenAsTheSdkDoes(factory)

        assertTrue(transport.requests[1].headers.contains("Last-Event-ID" to "ev_2"))
    }

    @Test
    fun anEmptyIdResetsTheResumptionInsteadOfFallingBackToTheSdksId() {
        val transport =
            FakeTransport(
                eventStream("id: ev_2\ndata: {}\n\nid\ndata: {}\n\n"),
                eventStream()
            )
        val factory = EventSources.createFactory(client(transport))

        assertEquals(listOf("ev_2", null), listenAsTheSdkDoes(factory))
        listenAsTheSdkDoes(factory)

        assertTrue(transport.requests[1].headers.none { (name, _) -> name.equals("Last-Event-ID", true) })
    }

    @Test
    fun linesEndingInCrlfSplitAcrossReadsAreFieldsToo() {
        // okhttp-sse reads an id field up to a LF, so a stream it can follow ends its lines in LF or CRLF.
        val transport =
            FakeTransport(
                eventStream("id: ev_2\r\ndata: {}\r\n\r\nid: ev_3\r", "\ndata: {}\r\n\r", "\n"),
                eventStream()
            )
        val factory = EventSources.createFactory(client(transport))

        assertEquals(listOf("ev_2", "ev_3"), listenAsTheSdkDoes(factory))
        listenAsTheSdkDoes(factory)

        assertTrue(transport.requests[1].headers.contains("Last-Event-ID" to "ev_3"))
    }

    /**
     * As the SDK listens (RestRepository.listenEvents through an okhttp-sse EventSource) and retries
     * (SpendViewModel.listenEvents: the same request, with the Last-Event-ID it read when it subscribed).
     */
    @Test
    fun anSdkEventSourceRetryingWithItsFirstIdResumesAfterTheLatestEvent() {
        val transport =
            FakeTransport(
                eventStream(
                    "id: ev_2\nevent: commerce_session.created\ndata: {}\n\n",
                    "id: ev_3\nevent: commerce_session.closed\ndata: {}\n\n"
                ),
                eventStream("id: ev_4\nevent: commerce_session.completed\ndata: {}\n\n")
            )
        val factory = EventSources.createFactory(client(transport))

        assertEquals(listOf("ev_2", "ev_3", "ev_4"), listenAsTheSdkDoes(factory) + listenAsTheSdkDoes(factory))
        assertTrue(transport.requests[0].headers.contains("Last-Event-ID" to "ev_1"))
        assertTrue(transport.requests[1].headers.contains("Last-Event-ID" to "ev_3"))
        assertEquals(1, transport.requests[1].headers.count { (name, _) -> name.equals("Last-Event-ID", true) })
    }

    @Test
    fun aJsonAnswerOnTheEventStreamPathEndsCleanly() {
        val transport =
            FakeTransport(
                { listener ->
                    listener.onHead(401, listOf("content-type" to "application/json"))
                    listener.onData("""{"error":{}}""".toByteArray())
                    listener.onEnd()
                }
            )

        val response = client(transport).newCall(eventsRequest()).execute()

        assertEquals(401, response.code)
        assertEquals("""{"error":{}}""", response.body.string())
    }

    @Test
    fun aStreamThatFailsBeforeItsHeadFailsTheCall() {
        val transport = FakeTransport({ throw OhttpException("relay answered 502", 502) })

        assertFailsWith<OhttpException> { client(transport).newCall(eventsRequest()).execute() }
    }

    @Test
    fun cancellingTheCallStopsTheStream() {
        val stopped = CompletableDeferred<Unit>()
        val transport =
            FakeTransport(
                { listener ->
                    listener.onHead(200, listOf("content-type" to "text/event-stream"))
                    listener.onData(": open\n\n".toByteArray())
                    try {
                        awaitCancellation()
                    } finally {
                        stopped.complete(Unit)
                    }
                }
            )
        val call = client(transport).newCall(eventsRequest())
        val response = call.execute()
        assertEquals(": open", response.body.source().readUtf8LineStrict())

        val reader = thread { runCatching { response.body.source().readUtf8Line() } }
        call.cancel()

        runBlocking { withTimeout(WAIT_S * MILLIS) { stopped.await() } }
        reader.join(WAIT_S * MILLIS)
        assertNull(reader.takeIf { it.isAlive }, "the blocked read is released")
    }

    @Test
    fun eventIdFieldsAreParsedAsServerSentEventsDefineThem() {
        assertEquals("ev_1", eventId("id: ev_1"))
        assertEquals("ev_1", eventId("id:ev_1"))
        assertEquals(" ev_1", eventId("id:  ev_1"))
        assertEquals("", eventId("id"))
        assertNull(eventId("data: id: ev_1"))
        assertNull(eventId("idx: 1"))
        assertNull(eventId("id: a\u0000b"))
    }

    /** One subscription of the SDK: the ids of the events it got, until the stream failed (the gateway's cut). */
    private fun listenAsTheSdkDoes(factory: EventSource.Factory): List<String?> {
        val ids = CopyOnWriteArrayList<String?>()
        val ended = CompletableDeferred<Throwable?>()
        factory.newEventSource(
            eventsRequest(),
            object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    ids += id?.ifEmpty { null }
                }

                override fun onClosed(eventSource: EventSource) {
                    ended.complete(null)
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    ended.complete(t)
                }
            }
        )
        assertIs<IOException>(runBlocking { withTimeout(WAIT_S * MILLIS) { ended.await() } })
        return ids.toList()
    }

    private fun eventStream(vararg chunks: String): suspend (OhttpHttpClient.ResponseListener) -> Unit =
        { listener ->
            listener.onHead(200, listOf("content-type" to "text/event-stream"))
            chunks.forEach { listener.onData(it.toByteArray()) }
            listener.onEnd()
        }

    /** A connection whose read blocks until it is closed, as DirectOhttpSender closes it on cancellation. */
    private class FakeSocket {
        val closed = CountDownLatch(1)
    }

    private suspend fun blockingRead(sockets: MutableList<FakeSocket>) {
        val socket = FakeSocket().also { sockets += it }
        coroutineScope {
            launch {
                try {
                    awaitCancellation()
                } finally {
                    socket.closed.countDown()
                }
            }
            runInterruptible(Dispatchers.IO) { socket.closed.await() }
            coroutineContext.cancelChildren()
        }
    }

    private fun awaitSocket(
        sockets: List<FakeSocket>,
        index: Int
    ) = runBlocking { withTimeout(WAIT_S * MILLIS) { while (sockets.size <= index) delay(POLL_MS) } }

    @Test
    fun thePlatformUserAgentIsNotForwardedToTheInnerRequest() {
        val transport =
            FakeTransport(
                answer = {
                    OhttpHttpClient.Response(200, ByteArray(0))
                }
            )

        client(transport)
            .newCall(
                Request
                    .Builder()
                    .url("https://api.flexa.co/accounts/me")
                    .header("User-Agent", "FlexaSDK/1.2.3 Android/13")
                    .build()
            ).execute()

        val sent = transport.requests.single()
        assertTrue(
            sent.headers.none { (name, _) -> name.equals("User-Agent", ignoreCase = true) },
            "User-Agent stays on the outer hop per ZIP #10 lines 704-707"
        )
    }

    private fun accountRequest() = Request.Builder().url("https://api.flexa.co/accounts/me").build()

    private fun eventsRequest() =
        Request
            .Builder()
            .url("https://api.flexa.co/events?type=commerce_session.created")
            .header("Accept", "text/event-stream")
            .header("Last-Event-ID", "ev_1")
            .build()

    private class FakeTransport(
        vararg streams: suspend (OhttpHttpClient.ResponseListener) -> Unit,
        private val answer: suspend () -> OhttpHttpClient.Response = { OhttpHttpClient.Response(200, ByteArray(0)) },
    ) : FlexaOhttpTransport {
        private val streams = ArrayDeque(streams.toList())
        val requests = CopyOnWriteArrayList<OhttpHttpClient.Request>()
        val timeouts = CopyOnWriteArrayList<Int>()

        override suspend fun execute(
            request: OhttpHttpClient.Request,
            timeoutMs: Int
        ): OhttpHttpClient.Response {
            requests += request
            timeouts += timeoutMs
            return answer()
        }

        override suspend fun executeStreaming(
            request: OhttpHttpClient.Request,
            timeoutMs: Int,
            listener: OhttpHttpClient.ResponseListener
        ) {
            requests += request
            timeouts += timeoutMs
            streams.removeFirst()(listener)
        }
    }

    private companion object {
        const val AUTHORIZATION = "Basic OnB1Ymxpc2hhYmxlX2xpdmVfem9kbF9nYXRld2F5X3BsYWNlaG9sZGVy"
        const val DATE = "Sun, 18 Oct 2026 12:00:00 GMT"
        const val STREAM_TIMEOUT_MS = 90_000
        const val WAIT_S = 10L
        const val CALL_TIMEOUT_MS = 300L
        const val POLL_MS = 10L
        const val MILLIS = 1000L
    }
}
