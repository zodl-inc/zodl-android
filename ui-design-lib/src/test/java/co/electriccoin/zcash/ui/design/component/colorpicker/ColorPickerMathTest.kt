package co.electriccoin.zcash.ui.design.component.colorpicker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The picker's colour conversions: HSV both ways, the spectrum mapping and its inverse, hex and
 * channel parsing, `#RRGGBB` both ways with the legacy `#RRGGBBAA` read as opaque, and the
 * white-or-black choice by WCAG's 3:1 contrast for graphics.
 */
class ColorPickerMathTest {
    @Test
    fun hsvRoundTripsThroughRgb() {
        listOf(Color.Red, Color.Green, Color.Blue, Color(0xFFBE38F3), Color(0xFF6155F5), Color.Gray).forEach { color ->
            assertEquals(color.toArgb(), color.toHsv().toColor().toArgb())
        }
        assertEquals(Hsv(hue = 0f, saturation = 0f, value = 1f), Color.White.toHsv())
        assertEquals(240f, Color.Blue.toHsv().hue)
    }

    @Test
    fun theSpectrumGoesFromWhiteThroughTheHueToBlack() {
        assertEquals(Color.White.toArgb(), spectrumColor(Offset(0.3f, 0f)).toArgb())
        assertEquals(Color.Red.toArgb(), spectrumColor(Offset(0f, 0.5f)).toArgb())
        assertEquals(Color.Black.toArgb(), spectrumColor(Offset(0.7f, 1f)).toArgb())
        assertEquals(Color.Cyan.toArgb(), spectrumColor(Offset(0.5f, 0.5f)).toArgb())
    }

    @Test
    fun aSpectrumColorMapsBackToItsPosition() {
        listOf(Offset(0.25f, 0.2f), Offset(0.6f, 0.5f), Offset(0.9f, 0.8f)).forEach { position ->
            val back = spectrumColor(position).spectrumPosition()
            assertTrue(abs(back.x - position.x) < TOLERANCE, "$position -> $back")
            assertTrue(abs(back.y - position.y) < TOLERANCE, "$position -> $back")
        }
    }

    @Test
    fun hexIsSixUpperCaseDigitsWithoutAlpha() {
        assertEquals("BE38F3", Color(0x80BE38F3).toHex())
        assertEquals("00000A", Color(0xFF00000A).toHex())
    }

    @Test
    fun hexParsesSixDigitsWithOrWithoutHashAsOpaque() {
        assertEquals(0xFFBE38F3.toInt(), parseHexColor("#be38f3")?.toArgb())
        assertEquals(0xFFBE38F3.toInt(), parseHexColor("BE38F3")?.toArgb())
        assertNull(parseHexColor("BE38F"))
        assertNull(parseHexColor("BE38FG"))
        assertNull(parseHexColor("80BE38F3"))
        assertEquals("BE38F3", filterHexInput("#be-38f3ff"))
    }

    @Test
    fun channelsReadAndWrite0To255() {
        val color = Color(0x80BE38F3)
        assertEquals(190, color.channel(ColorChannel.RED))
        assertEquals(56, color.channel(ColorChannel.GREEN))
        assertEquals(243, color.channel(ColorChannel.BLUE))
        assertEquals(0x80FF38F3.toInt(), color.withChannel(ColorChannel.RED, 300).toArgb())
        assertEquals(0x800038F3.toInt(), color.withChannel(ColorChannel.RED, 0).toArgb())
        assertEquals(128, parseChannelInput("128"))
        assertNull(parseChannelInput("256"))
        assertNull(parseChannelInput(""))
    }

    @Test
    fun rgbHexRoundTripsAndReadsTheLegacyFormAsOpaque() {
        assertEquals("#BE38F3", Color(0xFFBE38F3).toRgbHex())
        assertEquals("#00000A", Color(0xFF00000A).toRgbHex())
        assertEquals(0xFFBE38F3.toInt(), parseRgbHex("#be38f3")?.toArgb())
        assertEquals(0xFFBE38F3.toInt(), parseRgbHex("#be38f380")?.toArgb())
        assertNull(parseRgbHex("BE38F3"))
        assertNull(parseRgbHex("#BE38F"))
        assertNull(parseRgbHex("#BE38F38G"))
    }

    /** White wherever it reaches WCAG's 3:1 for graphics, as Figma's white eye on #BE38F3 does. */
    @Test
    fun whiteIsPickedFromAContrastOfThreeToOne() {
        val expected =
            mapOf(
                0xFF34C759 to Color.Black,
                0xFFFFCC00 to Color.Black,
                0xFFFF8D28 to Color.Black,
                0xFFFF383C to Color.White,
                0xFFCB30E0 to Color.White,
                0xFF0088FF to Color.White,
                0xFF6155F5 to Color.White,
                0xFFFF2D55 to Color.White,
                0xFFAC7F5E to Color.White,
                0xFF00C3D0 to Color.Black,
            )
        assertEquals(ColorPickerPalette.presets.map { it.toArgb() }, expected.keys.map { it.toInt() })
        expected.forEach { (argb, content) -> assertEquals(content, contrastingContentColor(Color(argb)), "$argb") }
        assertEquals(Color.White, contrastingContentColor(Color(0xFFBE38F3)))
        assertEquals(Color.Black, contrastingContentColor(Color.White))
        assertEquals(Color.White, contrastingContentColor(Color.Black))
        assertEquals(Color.White, contrastingContentColor(Color(0xFF808080)))
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
    }

    /** The 3:1 line falls between two neighbouring greys: white still reaches it on the darker one. */
    @Test
    fun whiteStopsJustAboveTheThreeToOneLine() {
        val lastWhite = Color(0xFF949494)
        val firstBlack = Color(0xFF959595)
        assertEquals(3.03f, contrastRatio(Color.White, lastWhite), TOLERANCE)
        assertEquals(2.99f, contrastRatio(Color.White, firstBlack), TOLERANCE)
        assertEquals(Color.White, contrastingContentColor(lastWhite))
        assertEquals(Color.Black, contrastingContentColor(firstBlack))
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
