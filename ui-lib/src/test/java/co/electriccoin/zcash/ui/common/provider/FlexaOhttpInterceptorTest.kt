package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.lightwallet.client.OhttpException
import co.electriccoin.lightwallet.client.OhttpHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
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
        const val MILLIS = 1000L
    }
}
