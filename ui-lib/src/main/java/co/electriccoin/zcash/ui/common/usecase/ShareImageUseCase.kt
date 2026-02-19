package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import android.graphics.Bitmap
import co.electriccoin.zcash.spackle.getInternalCacheDirSuspend
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.provider.GetVersionInfoProvider
import co.electriccoin.zcash.ui.util.FileShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val CACHE_SUBDIR = "zodl_qr_images" // NON-NLS

class ShareImageUseCase(
    private val context: Context,
    private val versionInfoProvider: GetVersionInfoProvider
) {
    suspend operator fun invoke(
        shareImageBitmap: Bitmap,
        shareText: String? = null,
        sharePickerText: String,
        filePrefix: String = "",
        fileSuffix: String = ""
    ) = shareData(
        context = context,
        shareImageBitmap = shareImageBitmap,
        versionInfo = versionInfoProvider(),
        filePrefix = filePrefix,
        fileSuffix = fileSuffix,
        shareText = shareText,
        sharePickerText = sharePickerText,
    )

    private suspend fun shareData(
        context: Context,
        shareImageBitmap: Bitmap,
        shareText: String?,
        sharePickerText: String,
        versionInfo: VersionInfo,
        filePrefix: String,
        fileSuffix: String,
    ): Boolean =
        runCatching {
            val bitmapFile =
                withContext(Dispatchers.IO) {
                    val cacheDir = context.getInternalCacheDirSuspend(CACHE_SUBDIR)
                    if (cacheDir.exists()) cacheDir.listFiles()?.forEach { it.delete() }
                    File
                        .createTempFile(
                            filePrefix,
                            fileSuffix,
                            cacheDir,
                        ).also {
                            it.storeBitmap(shareImageBitmap)
                        }
                }

            val shareIntent =
                FileShareUtil.newShareContentIntent(
                    context = context,
                    dataFilePath = bitmapFile.absolutePath,
                    fileType = FileShareUtil.ZASHI_QR_CODE_MIME_TYPE,
                    shareText = shareText,
                    sharePickerText = sharePickerText,
                    versionInfo = versionInfo,
                )

            context.startActivity(shareIntent)
            true
        }.getOrElse { false }

    private suspend fun File.storeBitmap(bitmap: Bitmap) =
        withContext(Dispatchers.IO) {
            outputStream().use { fOut ->
                @Suppress("MagicNumber")
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut)
                fOut.flush()
            }
        }
}
