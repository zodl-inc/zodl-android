package co.electriccoin.zcash.ui.screen.scan.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.screen.scan.QrCodeAnalyzer
import co.electriccoin.zcash.ui.screen.scankeystone.view.FramePosition
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class QrCodeAnalyzerImpl(
    private val framePosition: FramePosition,
    private val onQrCodeScanned: (String) -> Unit,
) : QrCodeAnalyzer {
    private val supportedImageFormat = Barcode.FORMAT_QR_CODE

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        Twig.verbose { "Mlkit image proxy: ${imageProxy.imageInfo}" }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val bitmap = imageProxy.toBitmap()

            val rotatedBitmap = bitmap.rotate(imageProxy.imageInfo.rotationDegrees)
            val croppedBitmap = rotatedBitmap.crop(framePosition)

            // No rotation for cropped Bitmap
            val image = InputImage.fromBitmap(croppedBitmap, 0)

            Twig.verbose {
                "Scan result: " +
                    "Frame: $framePosition, "
                "Format: ${mediaImage.format}, " +
                    "Image width: ${mediaImage.width}, " +
                    "Image height: ${mediaImage.height}"
                "Rotation: ${imageProxy.imageInfo.rotationDegrees}"
            }

            // Configure Barcode Scanner Options
            val options =
                BarcodeScannerOptions
                    .Builder()
                    .setBarcodeFormats(supportedImageFormat)
                    // We could optionally use this to enhance scan success ratio. If it's specified, then the library
                    // will suggest zooming the camera if the barcode is too far away or too small to be detected.
                    // .setZoomSuggestionOptions()
                    .build()

            // Initialize Barcode Scanner
            val scanner = BarcodeScanning.getClient(options)

            scanner
                .process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { value ->
                            Twig.debug { "Mlkit barcode scanned (contents not logged), length: ${value.length}" }
                            onQrCodeScanned(value)
                            // Note that we only take the first code from the list of discovered codes
                            return@addOnSuccessListener
                        }
                    }
                }.addOnFailureListener { e ->
                    Twig.error(e) { "Barcode detection failed" }
                }.addOnCompleteListener {
                    // Close the image proxy
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

private fun Bitmap.rotate(rotationDegrees: Int): Bitmap {
    // Rotate the matrix by the specified degrees
    val matrix =
        Matrix().also {
            it.postRotate(rotationDegrees.toFloat())
        }
    return Bitmap.createBitmap(
        // source
        this,
        // x
        0,
        // y
        0,
        // width
        width,
        // height
        height,
        // matrix
        matrix,
        // filter (Filter for better quality)
        true
    )
}

/*
 * Crop Bitmap to the specified dimensions given by [FramePosition]
 */
@Suppress("UNUSED_PARAMETER")
private fun Bitmap.crop(framePosition: FramePosition): Bitmap {
    // TODO [#1380]: Leverage FramePosition in QrCodeAnalyzer
    // TODO [#1380]: https://github.com/Electric-Coin-Company/zashi-android/issues/1380
    return Bitmap.createBitmap(
        this,
        // left
        (width * LEFT_OFFSET).toInt(),
        // top
        (height * TOP_OFFSET).toInt(),
        // width
        (width * WIDTH_OFFSET).toInt(),
        // height
        (height * HEIGHT_OFFSET).toInt(),
    )
}

private const val LEFT_OFFSET = .15
private const val TOP_OFFSET = .25
private const val WIDTH_OFFSET = .7
private const val HEIGHT_OFFSET = .45
