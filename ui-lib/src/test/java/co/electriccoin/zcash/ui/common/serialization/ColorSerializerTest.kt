package co.electriccoin.zcash.ui.common.serialization

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [ColorSerializer] writing `#RRGGBB` upper-case, reading either case and the legacy `#RRGGBBAA` as
 * opaque, and rejecting the rest.
 */
class ColorSerializerTest {
    @Test
    fun aColorRoundTripsAsSixDigits() {
        val encoded = Json.encodeToString(ColorSerializer, Color(0xFFBE38F3))

        assertEquals("\"#BE38F3\"", encoded)
        assertEquals(Color(0xFFBE38F3), Json.decodeFromString(ColorSerializer, encoded))
    }

    @Test
    fun alphaIsNeverWritten() {
        assertEquals("\"#0088FF\"", Json.encodeToString(ColorSerializer, Color(0x800088FF)))
    }

    @Test
    fun lowerAndUpperCaseInputReadTheSame() {
        assertEquals(Color(0xFFBE38F3), Json.decodeFromString(ColorSerializer, "\"#be38f3\""))
        assertEquals(Color(0xFFBE38F3), Json.decodeFromString(ColorSerializer, "\"#BE38F3\""))
        assertEquals(Color(0xFFBE38F3), Json.decodeFromString(ColorSerializer, "\"#Be38F3\""))
    }

    @Test
    fun theLegacyEightDigitFormReadsAsOpaque() {
        assertEquals(Color(0xFFBE38F3), Json.decodeFromString(ColorSerializer, "\"#BE38F380\""))
        assertEquals(Color(0xFF0088FF), Json.decodeFromString(ColorSerializer, "\"#0088ff00\""))
    }

    @Test
    fun anythingElseFails() {
        listOf(
            "\"BE38F3\"",
            "\"#BE38F\"",
            "\"#BE38FG\"",
            "\"#BE38F38G\"",
            "\"#BE38F3800\"",
            "\"#BE38F38\"",
            "\"\"",
            "\"purple\""
        ).forEach { text ->
            assertFailsWith<SerializationException>(text) { Json.decodeFromString(ColorSerializer, text) }
        }
    }

    @Test
    fun surroundingWhitespaceIsRejected() {
        listOf("\" #BE38F3\"", "\"#BE38F3 \"", "\"#BE38F3  \"", "\"\\t#BE38F3\"").forEach { text ->
            assertFailsWith<SerializationException>(text) { Json.decodeFromString(ColorSerializer, text) }
        }
    }

    @Test
    fun aDoubleHashIsRejected() {
        assertFailsWith<SerializationException> { Json.decodeFromString(ColorSerializer, "\"##BE38F3\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(ColorSerializer, "\"##BE38F380\"") }
    }

    @Test
    fun aJsonNumberIsRejected() {
        listOf("12466419", "0xBE38F3", "1.5").forEach { text ->
            assertFailsWith<SerializationException>(text) { Json.decodeFromString(ColorSerializer, text) }
        }
    }
}
