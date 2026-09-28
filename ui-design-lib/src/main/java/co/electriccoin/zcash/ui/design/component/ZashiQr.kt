package co.electriccoin.zcash.ui.design.component

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.util.AndroidQrCodeImageGenerator
import co.electriccoin.zcash.ui.design.util.JvmQrCodeGenerator
import co.electriccoin.zcash.ui.design.util.QrCodeColors
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark

/**
 * A QR code card that opens itself fullscreen on tap. While [QrState.isBlurred] the code is blurred (dimmed
 * below Android S, with [ZashiHiddenContentOverlay] carrying [QrState.hiddenLabel]) and the tap is disabled.
 */
@Composable
fun ZashiQr(
    state: QrState,
    modifier: Modifier = Modifier,
    qrSize: Dp = ZashiQrDefaults.width,
    colors: QrCodeColors = QrCodeDefaults.colors(),
    contentPadding: PaddingValues = QrCodeDefaults.contentPadding(),
) {
    var isFullscreenDialogVisible by remember { mutableStateOf(false) }

    ZashiQrInternal(
        state = state,
        modifier =
            modifier
                .clickable(
                    enabled = !state.isBlurred,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        isFullscreenDialogVisible = true
                    }
                ),
        colors = colors,
        contentPadding = contentPadding,
        qrSize = qrSize,
        enableBitmapReload = !isFullscreenDialogVisible,
        centerImage = state.centerImage,
    )

    if (isFullscreenDialogVisible) {
        Dialog(
            onDismissRequest = { isFullscreenDialogVisible = false },
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true
                )
        ) {
            val parent = LocalView.current.parent
            val activityWindow = LocalActivity.current?.window
            val activityFlags = activityWindow?.attributes?.flags ?: 0
            val isActivitySecured = (activityFlags and WindowManager.LayoutParams.FLAG_SECURE) != 0
            SideEffect {
                (parent as? DialogWindowProvider)?.window?.let { window ->
                    window.setDimAmount(FULLSCREEN_DIM)
                    if (isActivitySecured) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
            BrightenScreen()
            ConfigurationOverride(isDarkTheme = false) {
                FullscreenDialogContent(
                    state = state,
                    onBack = { isFullscreenDialogVisible = false },
                )
            }
        }
    }
}

@Composable
private fun ZashiQrInternal(
    state: QrState,
    qrSize: Dp,
    colors: QrCodeColors,
    contentPadding: PaddingValues,
    enableBitmapReload: Boolean,
    @DrawableRes centerImage: Int?,
    modifier: Modifier = Modifier,
) {
    val qrSizePx = with(LocalDensity.current) { qrSize.roundToPx() }
    val blur by animateDpAsState(if (state.isBlurred) BLUR_RADIUS else 0.dp, label = "qrBlur")
    var bitmap: ImageBitmap by remember {
        mutableStateOf(getQrCode(state.qrData, qrSizePx, colors))
    }

    var reload by remember { mutableStateOf(false) }

    LaunchedEffect(state.qrData, qrSizePx, colors) {
        if (enableBitmapReload && reload) {
            bitmap = getQrCode(state.qrData, qrSizePx, colors)
        }

        reload = true
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius4xl),
        border = BorderStroke(width = 1.dp, color = colors.border).takeIf { colors.border.isSpecified },
        color = colors.background,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .blurCompat(blur, BLUR_RADIUS)
                        .padding(contentPadding)
            ) {
                Image(
                    modifier = Modifier,
                    bitmap = bitmap,
                    contentDescription = state.contentDescription?.getValue(),
                )

                if (centerImage != null) {
                    Image(
                        modifier =
                            Modifier
                                .size(64.dp)
                                .align(Alignment.Center),
                        painter = painterResource(centerImage),
                        contentDescription = null,
                    )
                }
            }
            ZashiHiddenContentOverlay(
                isHidden = state.isBlurred,
                text = state.hiddenLabel?.getValue(),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun FullscreenDialogContent(
    state: QrState,
    onBack: () -> Unit
) {
    val containerWidth = LocalWindowInfo.current.containerSize.widthDp
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                ).padding(start = 16.dp, end = 16.dp, bottom = 64.dp)
    ) {
        ZashiQrInternal(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
            state = state,
            contentPadding = PaddingValues(6.dp),
            colors = QrCodeDefaults.colors(border = Color.Unspecified),
            qrSize = if (containerWidth > 44.dp) containerWidth - 44.dp else containerWidth,
            enableBitmapReload = true,
            centerImage = state.centerImage,
        )
    }
}

private fun getQrCode(
    address: String,
    size: Int,
    colors: QrCodeColors
): ImageBitmap {
    val qrCodePixelArray = JvmQrCodeGenerator.generate(address, size)
    return AndroidQrCodeImageGenerator.generate(qrCodePixelArray, size, colors).asImageBitmap()
}

object ZashiQrDefaults {
    val width: Dp
        @Composable
        get() = (LocalConfiguration.current.screenWidthDp * WIDTH_RATIO).dp
}

private const val WIDTH_RATIO = 0.66

object QrCodeDefaults {
    fun contentPadding() = PaddingValues(16.dp)

    @Composable
    fun colors(
        background: Color = Color.White orDark ZashiColors.Surfaces.bgPrimary,
        foreground: Color = Color.Black orDark Color.White,
        border: Color = ZashiColors.Surfaces.strokePrimary
    ) = QrCodeColors(
        background = background,
        foreground = foreground,
        border = border
    )
}

data class QrState(
    val qrData: String,
    val contentDescription: StringResource? = null,
    @param:DrawableRes val centerImage: Int? = null,
    /** A blurred QR is not clickable, so it never opens its fullscreen dialog. */
    val isBlurred: Boolean = false,
    /** Shown in the reveal hint drawn over a blurred QR on devices that cannot blur (below Android S). */
    val hiddenLabel: StringResource? = null,
)

private const val FULLSCREEN_DIM = .9f

private val BLUR_RADIUS = 16.5.dp
