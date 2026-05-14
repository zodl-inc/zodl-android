package co.electriccoin.zcash.ui.screen.scankeystone.view

import android.Manifest
import android.view.ViewGroup
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.SmallTopAppBar
import co.electriccoin.zcash.ui.design.component.TopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiLinearProgressIndicator
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.scan.ScanScreenState
import co.electriccoin.zcash.ui.screen.scan.ScanTag
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import co.electriccoin.zcash.ui.screen.scan.util.QrCodeAnalyzerImpl
import co.electriccoin.zcash.ui.screen.scankeystone.model.ScanKeystoneState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.guava.await

@OptIn(ExperimentalPermissionsApi::class)
@Composable
@Suppress("LongParameterList", "UnusedMaterial3ScaffoldPaddingParameter", "LongMethod")
fun ScanKeystoneView(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onScan: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onScanStateChange: (ScanScreenState) -> Unit,
    validationResult: ScanValidationState,
    state: ScanKeystoneState,
) = ZcashTheme(forceDarkMode = true) {
    // forces dark theme for this screen
    val permissionState =
        if (LocalInspectionMode.current) {
            remember {
                object : PermissionState {
                    override val permission = Manifest.permission.CAMERA
                    override val status = PermissionStatus.Granted

                    override fun launchPermissionRequest() = Unit
                }
            }
        } else {
            rememberPermissionState(
                Manifest.permission.CAMERA
            )
        }

    val (scanState, setScanState) =
        if (LocalInspectionMode.current) {
            remember {
                mutableStateOf(ScanScreenState.Scanning)
            }
        } else {
            rememberSaveable {
                mutableStateOf(
                    if (permissionState.status.isGranted) {
                        ScanScreenState.Scanning
                    } else {
                        ScanScreenState.Permission
                    }
                )
            }
        }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        Box {
            ScanMainContent(
                state = state,
                validationResult = validationResult,
                onScan = onScan,
                onOpenSettings = onOpenSettings,
                onBack = onBack,
                onScanStateChange = onScanStateChange,
                permissionState = permissionState,
                scanState = scanState,
                setScanState = setScanState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (scanState != ScanScreenState.Scanning) {
                                ZcashTheme.colors.cameraDisabledBackgroundColor
                            } else {
                                Color.Black
                            }
                        )
                // Intentionally omitting paddingValues to have edge to edge design
            )

            ScanTopAppBar(
                onBack = onBack,
                showBack = scanState != ScanScreenState.Scanning,
            )
        }
    }
}

@Composable
fun ScanBottomItems(
    state: ScanKeystoneState,
    validationResult: ScanValidationState,
    scanState: ScanScreenState,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        var failureText: String? = null

        if (validationResult == ScanValidationState.INVALID) {
            failureText = stringResource(R.string.scan_keystone_failure)
        }

        // Check permission request result, if any
        failureText =
            when (scanState) {
                ScanScreenState.Permission -> {
                    stringResource(
                        id = R.string.scan_keystone_state_permission,
                        stringResource(id = R.string.app_name)
                    )
                }

                ScanScreenState.Failed -> {
                    stringResource(id = R.string.scan_keystone_state_failed)
                }

                ScanScreenState.Scanning -> {
                    failureText
                }
            }

        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = state.message.getValue(),
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (failureText != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painter = painterResource(R.drawable.ic_scan_info), contentDescription = failureText)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = failureText,
                    style = ZashiTypography.textXs,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(ScanTag.FAILED_TEXT_STATE)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        when (scanState) {
            ScanScreenState.Scanning, ScanScreenState.Failed -> {
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack,
                    text = stringResource(id = R.string.scan_keystone_cancel_button)
                )
            }

            ScanScreenState.Permission -> {
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenSettings,
                    text = stringResource(id = R.string.scan_keystone_settings_button)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
}

@Composable
private fun ScanTopAppBar(
    onBack: () -> Unit,
    showBack: Boolean
) {
    SmallTopAppBar(
        navigationAction = {
            if (showBack) {
                TopAppBarBackNavigation(
                    backText = stringResource(id = R.string.back_navigation).uppercase(),
                    backContentDescriptionText = stringResource(R.string.back_navigation_content_description),
                    onBack = onBack
                )
            }
        },
        colors = ZcashTheme.colors.transparentTopAppBarColors,
    )
}

const val CAMERA_TRANSLUCENT_BORDER = 0.5f

data class FramePosition(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float = right - left
    val height: Float = bottom - top
}

@OptIn(ExperimentalPermissionsApi::class)
@Suppress(
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
    "MagicNumber",
    "DestructuringDeclarationWithTooManyEntries"
)
@Composable
private fun ScanMainContent(
    state: ScanKeystoneState,
    validationResult: ScanValidationState,
    onScan: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onScanStateChange: (ScanScreenState) -> Unit,
    permissionState: PermissionState,
    scanState: ScanScreenState,
    setScanState: (ScanScreenState) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        (!permissionState.status.isGranted) -> {
            setScanState(ScanScreenState.Permission)
            if (permissionState.status.shouldShowRationale) {
                // Keep dark screen with a link to the app settings - user denied the permission previously
            } else {
                LaunchedEffect(key1 = true) {
                    permissionState.launchPermissionRequest()
                }
            }
        }

        (scanState == ScanScreenState.Failed) -> {
            // Keep current state
        }

        (permissionState.status.isGranted) -> {
            if (scanState != ScanScreenState.Scanning) {
                setScanState(ScanScreenState.Scanning)
            }
        }
    }

    val density = LocalDensity.current

    val decreaseCutoutSizeByPx = with(density) { 3.dp.toPx() }

    // Calculate the best frame size for the current device screen
    var scanFrameLayoutSize by remember { mutableStateOf<IntSize?>(null) }
    var scanFrameLayoutSizeWindow by remember { mutableStateOf<IntSize?>(null) }

    val (isTorchOn, setIsTorchOn) = rememberSaveable { mutableStateOf(false) }

    ConstraintLayout(modifier = modifier) {
        val cutoutWidth by remember {
            derivedStateOf {
                (scanFrameLayoutSize?.width ?: 0) - decreaseCutoutSizeByPx
            }
        }
        val cutoutHeight by remember {
            derivedStateOf {
                (scanFrameLayoutSize?.height ?: 0) - decreaseCutoutSizeByPx
            }
        }

        val layoutX by remember {
            derivedStateOf {
                val windowWidth = scanFrameLayoutSizeWindow?.width ?: 0

                windowWidth / 2 - cutoutWidth / 2
            }
        }
        val layoutY by remember {
            derivedStateOf {
                val windowHeight = scanFrameLayoutSizeWindow?.height ?: 0

                windowHeight / 2 - cutoutHeight / 2
            }
        }

        val framePosition =
            FramePosition(
                left = layoutX,
                top = layoutY,
                right = layoutX + cutoutWidth,
                bottom = layoutY + cutoutHeight,
            )

        val (frame, frameWindow, bottomItems, topAnchor) = createRefs()

        when (scanState) {
            ScanScreenState.Permission -> {
                // Keep initial ui state
                onScanStateChange(ScanScreenState.Permission)
            }

            ScanScreenState.Scanning -> {
                onScanStateChange(ScanScreenState.Scanning)

                if (!LocalInspectionMode.current) {
                    ScanCameraView(
                        framePosition = framePosition,
                        isTorchOn = isTorchOn,
                        onScan = onScan,
                        permissionState = permissionState,
                        setScanState = setScanState,
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    clipPath(
                        clipOp = ClipOp.Difference,
                        path =
                            Path().apply {
                                addRoundRect(
                                    roundRect =
                                        RoundRect(
                                            left = framePosition.left,
                                            top = framePosition.top,
                                            right = framePosition.right,
                                            bottom = framePosition.bottom,
                                            topLeftCornerRadius = CornerRadius(24.dp.toPx()),
                                            topRightCornerRadius = CornerRadius(24.dp.toPx()),
                                            bottomRightCornerRadius = CornerRadius(24.dp.toPx()),
                                            bottomLeftCornerRadius = CornerRadius(24.dp.toPx()),
                                        )
                                )
                            }
                    ) {
                        drawRect(Color.Black.copy(alpha = CAMERA_TRANSLUCENT_BORDER))
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .offset(
                                x = 0.dp,
                                y = with(density) { framePosition.top.toDp() - 84.dp }
                            ),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (state.progress != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${state.progress}%",
                                style = ZashiTypography.textMd,
                                fontWeight = FontWeight.SemiBold,
                                color = ZashiColors.Text.textPrimary
                            )

                            Spacer(Modifier.height(8.dp))

                            ZashiLinearProgressIndicator(
                                modifier = Modifier.padding(horizontal = 100.dp),
                                progress = state.progress / 100f
                            )
                        }
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .offset(
                                x = 0.dp,
                                y = with(density) { framePosition.bottom.toDp() }
                            ).padding(top = 36.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    ImageButton(
                        painter =
                            if (isTorchOn) {
                                painterResource(R.drawable.ic_scan_torch_off)
                            } else {
                                painterResource(R.drawable.ic_scan_torch)
                            },
                        contentDescription = stringResource(id = R.string.scan_keystone_torch_content_description),
                        onClick = {
                            setIsTorchOn(!isTorchOn)
                        }
                    )
                }
            }

            ScanScreenState.Failed -> {
                onScanStateChange(ScanScreenState.Failed)
            }
        }

        Box(
            modifier =
                Modifier
                    .constrainAs(frameWindow) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(bottomItems.top)
                        width = Dimension.matchParent
                        height = Dimension.fillToConstraints
                    }.onSizeChanged {
                        scanFrameLayoutSizeWindow = it
                    }
        )

        Box(
            modifier =
                Modifier
                    .constrainAs(frame) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start, 66.dp)
                        end.linkTo(parent.end, 66.dp)
                        bottom.linkTo(bottomItems.top)
                        this.height = Dimension.ratio("1:1.08") // height is 8% larger than width
                        width = Dimension.fillToConstraints
                    }.onSizeChanged {
                        scanFrameLayoutSize = it
                    },
            contentAlignment = Alignment.Center
        ) {
            ScanFrame(modifier = Modifier.fillMaxSize())
        }

        Spacer(
            modifier =
                Modifier
                    .fillMaxHeight(.285f)
                    .constrainAs(topAnchor) {
                        top.linkTo(parent.top)
                    },
        )

        Box(
            modifier =
                Modifier
                    .constrainAs(bottomItems) { bottom.linkTo(parent.bottom) }
        ) {
            ScanBottomItems(
                state = state,
                validationResult = validationResult,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                scanState = scanState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 24.dp
                        )
            )
        }
    }
}

@Composable
private fun ImageButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() }
    )
}

@Composable
fun ScanFrame(modifier: Modifier = Modifier) {
    @Suppress("MagicNumber")
    Box(
        modifier = modifier.testTag(ScanTag.QR_FRAME)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_scan_corner),
            tint = Color.White,
            contentDescription = null,
            modifier =
                Modifier
                    .rotate(270f)
                    .align(Alignment.TopStart),
        )
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_scan_corner),
            tint = Color.White,
            contentDescription = null,
            modifier =
                Modifier
                    .rotate(0f)
                    .align(Alignment.TopEnd),
        )
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_scan_corner),
            tint = Color.White,
            contentDescription = null,
            modifier =
                Modifier
                    .rotate(180f)
                    .align(Alignment.BottomStart),
        )
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_scan_corner),
            tint = Color.White,
            contentDescription = null,
            modifier =
                Modifier
                    .rotate(90f)
                    .align(Alignment.BottomEnd),
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Suppress("LongMethod")
@Composable
fun ScanCameraView(
    framePosition: FramePosition,
    isTorchOn: Boolean,
    onScan: (result: String) -> Unit,
    permissionState: PermissionState,
    setScanState: (ScanScreenState) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // We check the permission first, as the ProcessCameraProvider's emit won't be called again after
    // recomposition with the permission granted
    val cameraProviderFlow =
        if (permissionState.status.isGranted) {
            remember {
                flow<ProcessCameraProvider> { emit(ProcessCameraProvider.getInstance(context).await()) }
            }
        } else {
            null
        }

    val collectedCameraProvider = cameraProviderFlow?.collectAsState(initial = null)?.value

    val cameraController = remember { mutableStateOf<CameraControl?>(null) }
    cameraController.value?.enableTorch(isTorchOn)

    if (null == collectedCameraProvider) {
        // Show loading indicator
    } else {
        val contentDescription = stringResource(id = R.string.scan_preview_content_description)

        val imageAnalysis =
            ImageAnalysis
                .Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

        AndroidView(
            factory = { factoryContext ->
                val previewView =
                    PreviewView(factoryContext).apply {
                        this.scaleType = PreviewView.ScaleType.FILL_CENTER
                        layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                    }
                previewView.contentDescription = contentDescription
                val selector =
                    CameraSelector
                        .Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()
                val preview =
                    androidx.camera.core.Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }

                runCatching {
                    // We must unbind the use-cases before rebinding them
                    collectedCameraProvider.unbindAll()
                    cameraController.value =
                        collectedCameraProvider
                            .bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imageAnalysis
                            ).cameraControl
                }.onFailure {
                    Twig.error { "Scan QR failed in bind phase with: ${it.message}" }
                    setScanState(ScanScreenState.Failed)
                }

                previewView
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(ScanTag.CAMERA_VIEW)
        )

        imageAnalysis
            .qrCodeFlow(
                framePosition = framePosition,
            ).collectAsState(initial = null)
            .value
            ?.let {
                onScan(it)
            }
    }
}

// Using callbackFlow because QrCodeAnalyzer has a non-suspending callback which makes
// a basic flow builder not work here.
@Composable
fun ImageAnalysis.qrCodeFlow(framePosition: FramePosition): Flow<String> {
    val context = LocalContext.current

    return remember {
        callbackFlow {
            setAnalyzer(
                ContextCompat.getMainExecutor(context),
                QrCodeAnalyzerImpl(
                    framePosition = framePosition,
                    onQrCodeScanned = { result ->
                        Twig.debug { "Scan result onQrCodeScanned: $result" }
                        // Note that these callbacks aren't tied to the Compose lifecycle, so they could occur
                        // after the view goes away.  Collection needs to occur within the Compose lifecycle
                        // to make this not be a problem.
                        trySend(result)
                    }
                )
            )

            awaitClose {
                // Nothing to close
            }
        }
    }
}

@PreviewScreens
@Composable
private fun ScanPreview() =
    ZcashTheme {
        Surface(
            color = Color.Blue,
            shape = RectangleShape,
        ) {
            ScanKeystoneView(
                snackbarHostState = SnackbarHostState(),
                onBack = {},
                onScan = {},
                onOpenSettings = {},
                onScanStateChange = {},
                validationResult = ScanValidationState.INVALID,
                state =
                    ScanKeystoneState(
                        message = stringRes("Message"),
                        progress = null,
                    )
            )
        }
    }

@PreviewScreens
@Composable
private fun ScanProgressPreview() =
    ZcashTheme {
        Surface(
            color = Color.Blue,
            shape = RectangleShape,
        ) {
            ScanKeystoneView(
                snackbarHostState = SnackbarHostState(),
                onBack = {},
                onScan = {},
                onOpenSettings = {},
                onScanStateChange = {},
                validationResult = ScanValidationState.INVALID,
                state =
                    ScanKeystoneState(
                        message = stringRes("Message"),
                        progress = 50
                    )
            )
        }
    }
