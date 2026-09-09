package co.electriccoin.zcash.ui.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

/**
 * Like [BigDecimalSerializer] but tolerant of the value arriving as either a JSON string ("99.01") or a bare
 * JSON number (35.1). SwapKit mixes the two — amounts come back as strings while `price`/`price_usd` come back
 * as numbers — so the string-only [BigDecimalSerializer] would fail to deserialize the numeric fields.
 * Reading the raw [jsonPrimitive] content covers both forms. Serialization always emits a plain string.
 */
object LenientBigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: BigDecimal
    ) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        val jsonDecoder = decoder as? JsonDecoder
        return if (jsonDecoder != null) {
            BigDecimal(jsonDecoder.decodeJsonElement().jsonPrimitive.content)
        } else {
            BigDecimal(decoder.decodeString())
        }
    }
}
