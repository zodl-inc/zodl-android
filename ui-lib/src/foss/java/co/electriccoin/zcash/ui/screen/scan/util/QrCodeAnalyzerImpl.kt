package co.electriccoin.zcash.ui.screen.scan.util

import androidx.camera.core.ImageProxy
import co.electriccoin.zcash.ui.screen.scan.QrCodeAnalyzer
import co.electriccoin.zcash.ui.screen.scankeystone.view.FramePosition
import zxingcpp.BarcodeReader

@Suppress("UnusedPrivateMember")
class QrCodeAnalyzerImpl(
    private val framePosition: FramePosition,
    private val onQrCodeScanned: (String) -> Unit,
) : QrCodeAnalyzer {
    private val barcodeReader =
        BarcodeReader(
            BarcodeReader.Options(
                formats = setOf(BarcodeReader.Format.QR_CODE),
                tryHarder = true,
                tryRotate = true,
                tryInvert = true,
            )
        )

    override fun analyze(image: ImageProxy) {
        image.use {
            barcodeReader
                .read(it)
                .firstOrNull()
                ?.text
                ?.let(onQrCodeScanned)
        }
    }
}
