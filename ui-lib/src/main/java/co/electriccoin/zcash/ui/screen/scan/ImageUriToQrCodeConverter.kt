package co.electriccoin.zcash.ui.screen.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import co.electriccoin.zcash.spackle.Twig
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ImageToQrCodeResult {
    data class SingleCode(
        val text: String
    ) : ImageToQrCodeResult()

    data object MultipleCodes : ImageToQrCodeResult()

    data object NoCode : ImageToQrCodeResult()
}

class ImageUriToQrCodeConverter {
    suspend operator fun invoke(
        context: Context,
        uri: Uri
    ): ImageToQrCodeResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val binaryBitmap =
                    uri
                        .toBitmap(context)
                        .toBinaryBitmap()

                val reader = QRCodeMultiReader()

                val results = reader.decodeMultiple(binaryBitmap)

                when (results?.size) {
                    null, 0 -> ImageToQrCodeResult.NoCode
                    1 -> ImageToQrCodeResult.SingleCode(results[0].text)
                    else -> ImageToQrCodeResult.MultipleCodes
                }
            }.onFailure {
                Twig.error(it) { "Failed to convert Uri to QR code" }
            }.getOrDefault(ImageToQrCodeResult.NoCode)
        }

    /**
     * Decodes the image scaled down to at most [MAX_DECODED_PIXELS], so that an image of any size, which any installed
     * app can share into the wallet, cannot exhaust memory. A QR code stays readable at that size.
     */
    private fun Uri.toBitmap(context: Context): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(this).use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        return requireNotNull(
            context.contentResolver.openInputStream(this).use { BitmapFactory.decodeStream(it, null, options) }
        ) { "Image could not be decoded" }
    }

    private fun Bitmap.toBinaryBitmap(): BinaryBitmap {
        val width = this.width
        val height = this.height
        val pixels = IntArray(width * height)
        this.getPixels(pixels, 0, width, 0, 0, width, height)
        this.recycle()
        val source = RGBLuminanceSource(width, height, pixels)
        return BinaryBitmap(HybridBinarizer(source))
    }

    internal companion object {
        /** 2048 x 2048: 16 MB for the bitmap and as much again for its pixels. A phone screenshot fits unscaled. */
        const val MAX_DECODED_PIXELS = 2048L * 2048L

        /** The smallest power of two that brings a [width] x [height] image down to [MAX_DECODED_PIXELS]. */
        fun sampleSizeFor(
            width: Int,
            height: Int
        ): Int {
            var sampleSize = 1
            while (width.toLong() / sampleSize * (height.toLong() / sampleSize) > MAX_DECODED_PIXELS) {
                sampleSize *= 2
            }
            return sampleSize
        }
    }
}
