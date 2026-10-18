package com.flexa.core.shared

import android.graphics.Color
import com.google.gson.Gson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


class SerializerProvider {


    val json by lazy(LazyThreadSafetyMode.NONE) {
        Gson()
    }
}

object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("com.flexa.core.shared.Color", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Color) {
        val raw = value.toArgb()
        encoder.encodeInt(raw)
    }

    override fun deserialize(decoder: Decoder): Color {
        val raw = decoder.decodeInt()
        return Color.valueOf(raw)
    }
}