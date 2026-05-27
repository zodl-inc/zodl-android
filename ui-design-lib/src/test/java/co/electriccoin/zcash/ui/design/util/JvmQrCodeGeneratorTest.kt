package co.electriccoin.zcash.ui.design.util

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmQrCodeGeneratorTest {
    // region Basic Generation

    @Test
    fun generate_validString_returnsCorrectSize() {
        val size = 256
        val result = JvmQrCodeGenerator.generate("zcash:t1testaddress123", size)

        assertEquals(size * size, result.size, "Result array should be sizePixels^2")
    }

    @Test
    fun generate_validString_containsBothBlackAndWhitePixels() {
        val result = JvmQrCodeGenerator.generate("test-data", 256)

        val hasBlack = result.any { it }
        val hasWhite = result.any { !it }

        assertTrue(hasBlack, "QR code should contain black pixels (true)")
        assertTrue(hasWhite, "QR code should contain white pixels (false)")
    }

    @Test
    fun generate_emptyString_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            JvmQrCodeGenerator.generate("", 256)
        }
    }

    // endregion

    // region Determinism

    @Test
    fun generate_sameInput_producesSameOutput() {
        val input = "zcash:t1deterministic"
        val size = 256

        val result1 = JvmQrCodeGenerator.generate(input, size)
        val result2 = JvmQrCodeGenerator.generate(input, size)

        assertTrue(result1.contentEquals(result2), "Same input should produce identical QR codes")
    }

    @Test
    fun generate_differentInputs_produceDifferentOutput() {
        val size = 256
        val result1 = JvmQrCodeGenerator.generate("address_one", size)
        val result2 = JvmQrCodeGenerator.generate("address_two", size)

        assertFalse(result1.contentEquals(result2), "Different inputs should produce different QR codes")
    }

    // endregion

    // region Size Variations

    @Test
    fun generate_smallSize_succeeds() {
        val size = 64
        val result = JvmQrCodeGenerator.generate("small", size)

        assertEquals(size * size, result.size)
    }

    @Test
    fun generate_largeSize_succeeds() {
        val size = 1024
        val result = JvmQrCodeGenerator.generate("large", size)

        assertEquals(size * size, result.size)
    }

    @Test
    fun generate_minimumSize_succeeds() {
        // QR code version 1 is 21x21 modules, with margin of 2 that's at least 25
        val size = 25
        val result = JvmQrCodeGenerator.generate("a", size)

        assertEquals(size * size, result.size)
    }

    // endregion

    // region Content Types

    @Test
    fun generate_zcashAddress_succeeds() {
        val address = "t1gXqfSSQt6WfpwyuCU3Wi7sSVZ66DYQ3Po"
        val result = JvmQrCodeGenerator.generate(address, 256)

        assertTrue(result.any { it }, "Zcash address QR should have content")
    }

    @Test
    fun generate_zip321URI_succeeds() {
        val uri = "zcash:t1testaddr?amount=1.5&memo=Test%20memo"
        val result = JvmQrCodeGenerator.generate(uri, 256)

        assertTrue(result.any { it }, "ZIP 321 URI QR should have content")
    }

    @Test
    fun generate_zip321URI_multipleParams_succeeds() {
        val uri = "zcash:t1testaddr?amount=0.001&memo=Payment%20for%20coffee&message=Thanks"
        val result = JvmQrCodeGenerator.generate(uri, 256)

        assertTrue(result.any { it })
    }

    @Test
    fun generate_longUnifiedAddress_succeeds() {
        // Unified addresses can be 300+ chars
        val longAddress = "u" + "a".repeat(300)
        val result = JvmQrCodeGenerator.generate(longAddress, 512)

        assertEquals(512 * 512, result.size)
        assertTrue(result.any { it })
    }

    @Test
    fun generate_unicodeContent_succeeds() {
        val unicode = "zcash:t1addr?memo=Pago%20🎉%20gracias"
        val result = JvmQrCodeGenerator.generate(unicode, 256)

        assertTrue(result.any { it })
    }

    // endregion

    // region Edge Cases

    @Test
    fun generate_zeroSize_returnsEmptyArray() {
        val result = JvmQrCodeGenerator.generate("test", 0)

        assertEquals(0, result.size, "Size 0 should produce empty array")
    }

    @Test
    fun generate_negativeSize_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            JvmQrCodeGenerator.generate("test", -1)
        }
    }

    // endregion

    // region Margin Constant

    @Test
    fun marginConstant_isTwo() {
        assertEquals(2, QR_CODE_IMAGE_MARGIN_IN_PIXELS, "QR code margin should be 2 pixels")
    }

    // endregion

    // region QR Decode Verification (round-trip)

    /**
     * LuminanceSource backed by the BooleanArray from JvmQrCodeGenerator.
     * ZXing's QRCodeReader needs a LuminanceSource to decode — this converts
     * our boolean pixel matrix into grayscale luminance values.
     */
    private class BooleanArrayLuminanceSource(
        private val pixels: BooleanArray,
        private val size: Int
    ) : LuminanceSource(size, size) {
        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val result = row ?: ByteArray(size)
            for (x in 0 until size) {
                // true = black (0), false = white (255)
                result[x] = if (pixels[y * size + x]) 0 else 0xFF.toByte()
            }
            return result
        }

        override fun getMatrix(): ByteArray {
            val matrix = ByteArray(size * size)
            for (i in pixels.indices) {
                matrix[i] = if (pixels[i]) 0 else 0xFF.toByte()
            }
            return matrix
        }
    }

    /** Decode a BooleanArray QR code back to its encoded string */
    private fun decodeQR(pixels: BooleanArray, size: Int): String {
        val source = BooleanArrayLuminanceSource(pixels, size)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return QRCodeReader().decode(bitmap).text
    }

    @Test
    fun roundTrip_simpleAddress_decodesCorrectly() {
        val input = "t1gXqfSSQt6WfpwyuCU3Wi7sSVZ66DYQ3Po"
        val size = 256
        val pixels = JvmQrCodeGenerator.generate(input, size)

        val decoded = decodeQR(pixels, size)

        assertEquals(input, decoded, "Decoded QR should match input address")
    }

    @Test
    fun roundTrip_zip321URI_decodesCorrectly() {
        val input = "zcash:t1testaddr?amount=1.5&memo=Test%20memo"
        val size = 256
        val pixels = JvmQrCodeGenerator.generate(input, size)

        val decoded = decodeQR(pixels, size)

        assertEquals(input, decoded, "Decoded QR should match ZIP 321 URI")
    }

    @Test
    fun roundTrip_zip321URI_withAllParams_decodesCorrectly() {
        val input = "zcash:t1addr?amount=0.001&memo=Payment%20for%20coffee&message=Thanks"
        val size = 256
        val pixels = JvmQrCodeGenerator.generate(input, size)

        val decoded = decodeQR(pixels, size)

        assertEquals(input, decoded, "Decoded QR should preserve all ZIP 321 params")
    }

    @Test
    fun roundTrip_urlEncodedContent_decodesCorrectly() {
        val input = "zcash:t1addr?memo=Gracias%20por%20tu%20compra"
        val size = 256
        val pixels = JvmQrCodeGenerator.generate(input, size)

        val decoded = decodeQR(pixels, size)

        assertEquals(input, decoded, "Decoded QR should preserve URL-encoded content")
    }

    @Test
    fun roundTrip_longUnifiedAddress_decodesCorrectly() {
        val input = "u1" + "abcdef1234".repeat(15)
        val size = 512
        val pixels = JvmQrCodeGenerator.generate(input, size)

        val decoded = decodeQR(pixels, size)

        assertEquals(input, decoded, "Decoded QR should match long unified address exactly")
    }

    @Test
    fun roundTrip_singleCharacter_decodesCorrectly() {
        val input = "z"
        val size = 256
        val pixels = JvmQrCodeGenerator.generate(input, size)

        val decoded = decodeQR(pixels, size)

        assertEquals(input, decoded, "Decoded QR should match single character")
    }

    // endregion
}
