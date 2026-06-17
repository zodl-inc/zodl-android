package co.electriccoin.zcash.ui.common.component

import android.net.Uri
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Custom-server validation (MOB-1144): a manual endpoint typed by the user is parsed into a
 * [LightWalletEndpoint] only when it is well formed - a valid host and a port in 1..65535, with or
 * without a scheme - otherwise it is rejected (null). Malformed input is rejected by the format
 * regex before any URI parsing.
 */
class ZashiEndpointTextFieldParserTest {
    @BeforeTest
    fun setUp() {
        mockkStatic(Uri::class)
        // The androidx String.toUri() extension is inline and delegates to Uri.parse, so stubbing
        // Uri.parse covers the host/port extraction for well-formed inputs.
        every { Uri.parse(any()) } answers {
            val raw = firstArg<String>().substringAfter("://")
            mockk<Uri> {
                every { host } returns raw.substringBeforeLast(":")
                every { port } returns raw.substringAfterLast(":").toInt()
            }
        }
    }

    @AfterTest
    fun tearDown() = unmockkStatic(Uri::class)

    @Test
    fun parsesHostAndPortWithoutScheme() {
        assertEquals(
            LightWalletEndpoint("host.example.com", 443, true),
            ZashiEndpointTextFieldParser.toEndpointOrNull("host.example.com:443")
        )
    }

    @Test
    fun parsesHostAndPortWithScheme() {
        assertEquals(
            LightWalletEndpoint("host.example.com", 9067, true),
            ZashiEndpointTextFieldParser.toEndpointOrNull("https://host.example.com:9067")
        )
    }

    @Test
    fun rejectsInputWithoutPort() {
        assertNull(ZashiEndpointTextFieldParser.toEndpointOrNull("host.example.com"))
    }

    @Test
    fun rejectsEmptyInput() {
        assertNull(ZashiEndpointTextFieldParser.toEndpointOrNull(""))
    }

    @Test
    fun rejectsPortZero() {
        assertNull(ZashiEndpointTextFieldParser.toEndpointOrNull("host.example.com:0"))
    }

    @Test
    fun rejectsPortAboveMax() {
        assertNull(ZashiEndpointTextFieldParser.toEndpointOrNull("host.example.com:70000"))
    }

    @Test
    fun rejectsHostStartingWithDot() {
        assertNull(ZashiEndpointTextFieldParser.toEndpointOrNull(".host.example.com:443"))
    }

    @Test
    fun rejectsHostWithConsecutiveDots() {
        assertNull(ZashiEndpointTextFieldParser.toEndpointOrNull("host..example.com:443"))
    }
}
