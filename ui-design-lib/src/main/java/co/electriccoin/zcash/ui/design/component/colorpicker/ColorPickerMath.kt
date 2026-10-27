package co.electriccoin.zcash.ui.design.component.colorpicker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One of the three 0-255 channels the Sliders tab edits. */
enum class ColorChannel { RED, GREEN, BLUE }

/** A colour as hue (0-360), saturation and value (both 0-1). */
internal data class Hsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

internal fun Color.toHsv(): Hsv {
    val maxChannel = max(red, max(green, blue))
    val minChannel = min(red, min(green, blue))
    val delta = maxChannel - minChannel
    val hue =
        when {
            delta == 0f -> 0f
            maxChannel == red -> HUE_SECTOR * (((green - blue) / delta).mod(HUE_SECTORS))
            maxChannel == green -> HUE_SECTOR * ((blue - red) / delta + 2)
            else -> HUE_SECTOR * ((red - green) / delta + HUE_BLUE_OFFSET)
        }
    return Hsv(
        hue = hue,
        saturation = if (maxChannel == 0f) 0f else delta / maxChannel,
        value = maxChannel
    )
}

internal fun Hsv.toColor(): Color =
    Color.hsv(
        hue = hue.coerceIn(0f, MAX_HUE),
        saturation = saturation.coerceIn(0f, 1f),
        value = value.coerceIn(0f, 1f)
    )

/**
 * The colour of the spectrum at [position], both coordinates fractions of its size: the hue runs
 * left to right, and from top to bottom the colour goes from white through the full hue to black.
 */
fun spectrumColor(position: Offset): Color {
    val x = position.x.coerceIn(0f, 1f)
    val y = position.y.coerceIn(0f, 1f)
    return if (y <= HALF) {
        Hsv(hue = x * MAX_HUE, saturation = y / HALF, value = 1f).toColor()
    } else {
        Hsv(hue = x * MAX_HUE, saturation = 1f, value = 1f - (y - HALF) / HALF).toColor()
    }
}

/** Where [spectrumColor] draws this colour, or the closest point to it, as fractions of the spectrum. */
fun Color.spectrumPosition(): Offset {
    val hsv = toHsv()
    val y = if (hsv.value >= hsv.saturation) hsv.saturation * HALF else HALF + (1f - hsv.value) * HALF
    return Offset(hsv.hue / MAX_HUE, y)
}

fun Color.channel(channel: ColorChannel): Int =
    (
        when (channel) {
            ColorChannel.RED -> red
            ColorChannel.GREEN -> green
            ColorChannel.BLUE -> blue
        } * MAX_CHANNEL
    ).roundToInt()

fun Color.withChannel(
    channel: ColorChannel,
    value: Int
): Color {
    val fraction = value.coerceIn(0, MAX_CHANNEL) / MAX_CHANNEL.toFloat()
    return when (channel) {
        ColorChannel.RED -> copy(red = fraction)
        ColorChannel.GREEN -> copy(green = fraction)
        ColorChannel.BLUE -> copy(blue = fraction)
    }
}

/** The 0-255 value of [text] typed into a channel field, or null while it is not one. */
fun parseChannelInput(text: String): Int? = text.toIntOrNull()?.takeIf { it in 0..MAX_CHANNEL }

private const val MAX_HUE = 360f
private const val HUE_SECTOR = 60f
private const val HUE_SECTORS = 6f
private const val HUE_BLUE_OFFSET = 4
private const val HALF = 0.5f
private const val MAX_CHANNEL = 255
