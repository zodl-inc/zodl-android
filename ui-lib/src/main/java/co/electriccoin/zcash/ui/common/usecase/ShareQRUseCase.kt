package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import co.electriccoin.zcash.ui.design.util.AndroidQrCodeImageGenerator
import co.electriccoin.zcash.ui.design.util.JvmQrCodeGenerator
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.createConfiguration
import co.electriccoin.zcash.ui.design.util.getString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShareQRUseCase(
    private val shareImageBitmap: ShareImageUseCase,
    private val context: Context
) {
    @Suppress("MagicNumber")
    suspend operator fun invoke(
        qrData: String,
        shareText: StringResource,
        sharePickerText: StringResource,
        filenamePrefix: String,
        @DrawableRes centerIcon: Int? = null
    ) = withContext(Dispatchers.Default) {
        val qrCodePixelArray = JvmQrCodeGenerator.generate(data = qrData, sizePixels = QR_SIZE_PX)
        val bitmap =
            AndroidQrCodeImageGenerator
                .generate(
                    bitArray = qrCodePixelArray,
                    sizePixels = QR_SIZE_PX,
                    background = Color.White.toArgb(),
                    foreground = Color.Black.toArgb(),
                ).let {
                    if (centerIcon != null) it.copy(Bitmap.Config.ARGB_8888, true) else it
                }

        if (centerIcon != null) {
            val newContext = createLightThemeContext()
            val drawable = ContextCompat.getDrawable(newContext, centerIcon)
            if (drawable != null) {
                val canvas = Canvas(bitmap)
                val iconRadius = (bitmap.width * .09f).toInt()
                val bitmapRadius = bitmap.width / 2
                val left = bitmapRadius - iconRadius
                val right = bitmapRadius + iconRadius
                val top = bitmapRadius - iconRadius
                val bottom = bitmapRadius + iconRadius
                drawable.setBounds(left, top, right, bottom)
                drawable.draw(canvas)
            }
        }

        shareImageBitmap(
            shareImageBitmap =
                bitmap.let {
                    if (centerIcon != null) it.copy(Bitmap.Config.ARGB_8888, false) else it
                },
            filePrefix = filenamePrefix,
            fileSuffix = ".png",
            shareText = shareText.getString(context),
            sharePickerText = sharePickerText.getString(context),
        )
    }

    private fun createLightThemeContext(): Context {
        val newConfiguration = context.resources.configuration.createConfiguration(isDarkTheme = false)
        val newContext = context.createConfigurationContext(newConfiguration)
        return newContext
    }
}

private const val QR_SIZE_PX = 1280
