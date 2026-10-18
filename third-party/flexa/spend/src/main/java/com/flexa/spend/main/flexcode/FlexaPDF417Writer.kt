package com.flexa.spend.main.flexcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.Writer
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.pdf417.encoder.Compaction
import com.google.zxing.pdf417.encoder.Dimensions
import com.google.zxing.pdf417.encoder.PDF417
import java.nio.charset.Charset


class FlexaPDF417Writer(
    private val aspectRatio: Float
) : Writer {
    @Throws(WriterException::class)
    override fun encode(
        contents: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        hints: Map<EncodeHintType?, *>?
    ): BitMatrix {
        require(format == BarcodeFormat.PDF_417) { "Can only encode PDF_417, but got $format" }
        val encoder = PDF417()
        var margin = WHITE_SPACE
        var errorCorrectionLevel = DEFAULT_ERROR_CORRECTION_LEVEL
        var autoECI = false
        if (hints != null) {
            if (hints.containsKey(EncodeHintType.PDF417_COMPACT)) {
                encoder.setCompact(java.lang.Boolean.parseBoolean(hints[EncodeHintType.PDF417_COMPACT].toString()))
            }
            if (hints.containsKey(EncodeHintType.PDF417_COMPACTION)) {
                encoder.setCompaction(Compaction.valueOf(hints[EncodeHintType.PDF417_COMPACTION].toString()))
            }
            if (hints.containsKey(EncodeHintType.PDF417_DIMENSIONS)) {
                val dimensions = hints[EncodeHintType.PDF417_DIMENSIONS] as Dimensions?
                encoder.setDimensions(
                    dimensions!!.maxCols,
                    dimensions.minCols,
                    dimensions.maxRows,
                    dimensions.minRows
                )
            }
            if (hints.containsKey(EncodeHintType.MARGIN)) {
                margin = hints[EncodeHintType.MARGIN].toString().toInt()
            }
            if (hints.containsKey(EncodeHintType.ERROR_CORRECTION)) {
                errorCorrectionLevel = hints[EncodeHintType.ERROR_CORRECTION].toString().toInt()
            }
            if (hints.containsKey(EncodeHintType.CHARACTER_SET)) {
                val encoding = Charset.forName(hints[EncodeHintType.CHARACTER_SET].toString())
                encoder.setEncoding(encoding)
            }
            autoECI = hints.containsKey(EncodeHintType.PDF417_AUTO_ECI) &&
                    java.lang.Boolean.parseBoolean(hints[EncodeHintType.PDF417_AUTO_ECI].toString())
        }
        return bitMatrixFromEncoder(
            encoder,
            contents,
            errorCorrectionLevel,
            width,
            height,
            margin,
            autoECI,
            aspectRatio
        )
    }

    @Throws(WriterException::class)
    override fun encode(
        contents: String,
        format: BarcodeFormat,
        width: Int,
        height: Int
    ): BitMatrix {
        return encode(contents, format, width, height, null)
    }

    companion object {
        /**
         * default white space (margin) around the code
         */
        private const val WHITE_SPACE = 30

        /**
         * default error correction level
         */
        private const val DEFAULT_ERROR_CORRECTION_LEVEL = 2

        /**
         * Takes encoder, accounts for width/height, and retrieves bit matrix
         */
        @Throws(WriterException::class)
        private fun bitMatrixFromEncoder(
            encoder: PDF417,
            contents: String,
            errorCorrectionLevel: Int,
            width: Int,
            height: Int,
            margin: Int,
            autoECI: Boolean,
            ratio: Float,
        ): BitMatrix {
            encoder.generateBarcodeLogic(contents, errorCorrectionLevel, autoECI)
            val aspectRatio = ratio.toInt()
            var originalScale = encoder.barcodeMatrix.getScaledMatrix(1, aspectRatio)
            var rotated = false
            if (height > width != originalScale[0].size < originalScale.size) {
                originalScale = rotateArray(originalScale)
                rotated = true
            }
            val scaleX = width / originalScale[0].size
            val scaleY = height / originalScale.size
            val scale = scaleX.coerceAtMost(scaleY)
            if (scale > 1) {
                var scaledMatrix = encoder.barcodeMatrix.getScaledMatrix(scale, scale * aspectRatio)
                if (rotated) {
                    scaledMatrix = rotateArray(scaledMatrix)
                }
                return bitMatrixFromBitArray(scaledMatrix, margin)
            }
            return bitMatrixFromBitArray(originalScale, margin)
        }

        /**
         * This takes an array holding the values of the PDF 417
         *
         * @param input a byte array of information with 0 is black, and 1 is white
         * @param margin border around the barcode
         * @return BitMatrix of the input
         */
        private fun bitMatrixFromBitArray(input: Array<ByteArray>, margin: Int): BitMatrix {
            // Creates the bit matrix with extra space for whitespace
            val output = BitMatrix(input[0].size + 2 * margin, input.size + 2 * margin)
            output.clear()
            var y = 0
            var yOutput = output.height - margin - 1
            while (y < input.size) {
                val inputY = input[y]
                for (x in input[0].indices) {
                    // Zero is white in the byte matrix
                    if (inputY[x].toInt() == 1) {
                        output[x + margin] = yOutput
                    }
                }
                y++
                yOutput--
            }
            return output
        }

        /**
         * Takes and rotates the it 90 degrees
         */
        private fun rotateArray(bitarray: Array<ByteArray>): Array<ByteArray> {
            val temp = Array(bitarray[0].size) {
                ByteArray(
                    bitarray.size
                )
            }
            for (ii in bitarray.indices) {
                // This makes the direction consistent on screen when rotating the
                // screen;
                val inverseii = bitarray.size - ii - 1
                for (jj in bitarray[0].indices) {
                    temp[jj][inverseii] = bitarray[ii][jj]
                }
            }
            return temp
        }
    }
}
