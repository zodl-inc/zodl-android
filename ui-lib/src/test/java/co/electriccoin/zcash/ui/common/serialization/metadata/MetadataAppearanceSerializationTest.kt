package co.electriccoin.zcash.ui.common.serialization.metadata

import androidx.compose.ui.graphics.Color
import co.electriccoin.zcash.ui.common.model.metadata.AccountAppearanceMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.AccountMetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.model.metadata.SwapsMetadataV3
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The optional appearance block of a V3 metadata file: it round-trips as
 * `{"color":"#RRGGBB","lastUpdated":…}`, is left out when there is none, a file written before it
 * existed still reads, unknown keys are still ignored, an explicit null reads as none, and a legacy
 * eight-digit colour is written back as six digits.
 */
class MetadataAppearanceSerializationTest {
    private val serializer = MetadataSerializer()

    @Test
    fun anAppearanceRoundTrips() {
        val metadata = metadata(AccountAppearanceMetadataV3(Color(0xFFBE38F3), Instant.ofEpochMilli(2_000)))

        val json = write(metadata)

        assertTrue(json.contains(""""appearance":{"color":"#BE38F3","lastUpdated":2000}"""), json)
        assertEquals(metadata, read(json))
    }

    @Test
    fun noAppearanceWritesNoBlockAndRoundTrips() {
        val metadata = metadata(appearance = null)

        val json = write(metadata)

        assertFalse(json.contains("appearance"), json)
        assertEquals(metadata, read(json))
    }

    @Test
    fun aFileWrittenBeforeTheBlockExistedReads() {
        assertNull(read(json(appearance = null)).accountMetadata.appearance)
    }

    @Test
    fun unknownKeysAreStillIgnored() {
        val metadata =
            read(
                json(
                    appearance = """{"color":"#0088ffff","lastUpdated":7,"shape":"round"}""",
                    extra = ""","future":{"anything":1}"""
                )
            )

        assertEquals(
            AccountAppearanceMetadataV3(Color(0xFF0088FF), Instant.ofEpochMilli(7)),
            metadata.accountMetadata.appearance
        )
    }

    @Test
    fun anExplicitNullAppearanceReadsAsNone() {
        val metadata = read(json(appearance = "null"))

        assertNull(metadata.accountMetadata.appearance)
        assertEquals(listOf("r1"), metadata.accountMetadata.read)
    }

    @Test
    fun aLegacyEightDigitColorIsWrittenBackAsSixDigits() {
        val metadata = read(json(appearance = """{"color":"#BE38F380","lastUpdated":7}"""))

        val json = write(metadata)

        assertTrue(json.contains(""""color":"#BE38F3""""), json)
        assertFalse(json.contains("#BE38F380"), json)
    }

    private fun write(metadata: MetadataV3) =
        ByteArrayOutputStream().also { serializer.serialize(it, metadata) }.toString(Charsets.UTF_8)

    private fun read(json: String) = serializer.deserialize(json.byteInputStream())

    private fun json(
        appearance: String?,
        extra: String = ""
    ) = """{"version":3,"lastUpdated":1000,"accountMetadata":{"bookmarked":[],"read":["r1"],"annotations":[],""" +
        """"swaps":{"swapIds":[],"lastUsedAssetHistory":[]}""" +
        appearance?.let { ""","appearance":$it""" }.orEmpty() + extra + "}}"

    private fun metadata(appearance: AccountAppearanceMetadataV3?) =
        MetadataV3(
            lastUpdated = Instant.ofEpochMilli(1_000),
            accountMetadata =
                AccountMetadataV3(
                    bookmarked = emptyList(),
                    read = listOf("r1"),
                    annotations = emptyList(),
                    swaps = SwapsMetadataV3(swapIds = emptyList(), lastUsedAssetHistory = emptySet()),
                    appearance = appearance
                )
        )
}
