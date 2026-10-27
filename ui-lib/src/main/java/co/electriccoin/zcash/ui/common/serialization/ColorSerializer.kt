package co.electriccoin.zcash.ui.common.serialization

import androidx.compose.ui.graphics.Color
import co.electriccoin.zcash.ui.design.component.colorpicker.parseRgbHex
import co.electriccoin.zcash.ui.design.component.colorpicker.toRgbHex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An opaque colour as `#RRGGBB` in sRGB, written upper-case and read in either case. The `#RRGGBBAA`
 * of earlier builds still reads, as the opaque colour. Anything else fails with a
 * [SerializationException].
 */
internal object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Color", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Color
    ) {
        encoder.encodeString(value.toRgbHex())
    }

    override fun deserialize(decoder: Decoder): Color {
        val text = decoder.decodeString()
        return parseRgbHex(text) ?: throw SerializationException("Not a #RRGGBB colour")
    }
}
