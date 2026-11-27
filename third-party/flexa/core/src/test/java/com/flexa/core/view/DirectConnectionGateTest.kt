package com.flexa.core.view

import com.flexa.core.Flexa
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DirectConnectionGateTest {

    private var sent = 0

    /** Stands in for the network: counts what would have left the device. */
    private val network = Interceptor { chain ->
        sent++
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(ByteArray(0).toResponseBody())
            .build()
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(DirectConnectionGate)
        .addInterceptor(network)
        .build()

    private fun download() =
        client.newCall(Request.Builder().url("https://images.example/logo.png").build()).execute().close()

    @Before
    fun setUp() {
        Flexa.configured = true
    }

    @After
    fun tearDown() {
        Flexa.directConnectionsAllowed = null
        Flexa.configured = false
    }

    @Test
    fun `before init nothing is allowed, whatever the gate says`() {
        Flexa.configured = false
        Flexa.directConnectionsAllowed = { true }

        assertFalse(Flexa.allowsDirectConnections())
        try {
            download()
            throw AssertionError("the download should have failed")
        } catch (expected: IOException) {
            // a restored process shows the placeholder until the app has set the gate
        }
        assertEquals(0, sent)
    }

    @Test
    fun `without a gate direct connections are allowed, as upstream`() {
        assertTrue(Flexa.allowsDirectConnections())
        download()
        assertEquals(1, sent)
    }

    @Test
    fun `nothing is sent while direct connections are off`() {
        Flexa.directConnectionsAllowed = { false }

        assertFalse(Flexa.allowsDirectConnections())
        try {
            download()
            throw AssertionError("the download should have failed")
        } catch (expected: IOException) {
            // the image loader shows its placeholder
        }
        assertEquals(0, sent)
    }

    @Test
    fun `the gate is asked on every download`() {
        var allowed = false
        Flexa.directConnectionsAllowed = { allowed }

        runCatching { download() }
        allowed = true
        download()

        assertEquals(1, sent)
    }
}
