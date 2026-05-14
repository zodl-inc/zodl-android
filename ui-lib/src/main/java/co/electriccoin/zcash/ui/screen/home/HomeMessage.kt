package co.electriccoin.zcash.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.screen.home.backup.WalletBackupMessage
import co.electriccoin.zcash.ui.screen.home.backup.WalletBackupMessageState
import co.electriccoin.zcash.ui.screen.home.currency.EnableCurrencyConversionMessage
import co.electriccoin.zcash.ui.screen.home.currency.EnableCurrencyConversionMessageState
import co.electriccoin.zcash.ui.screen.home.disconnected.WalletDisconnectedMessage
import co.electriccoin.zcash.ui.screen.home.disconnected.WalletDisconnectedMessageState
import co.electriccoin.zcash.ui.screen.home.error.WalletErrorMessage
import co.electriccoin.zcash.ui.screen.home.error.WalletErrorMessageState
import co.electriccoin.zcash.ui.screen.home.orswap.ORSwapMessage
import co.electriccoin.zcash.ui.screen.home.orswap.ORSwapMessageState
import co.electriccoin.zcash.ui.screen.home.reporting.CrashReportMessage
import co.electriccoin.zcash.ui.screen.home.reporting.CrashReportMessageState
import co.electriccoin.zcash.ui.screen.home.restoring.WalletRestoringMessage
import co.electriccoin.zcash.ui.screen.home.restoring.WalletRestoringMessageState
import co.electriccoin.zcash.ui.screen.home.resyncing.WalletResyncingMessage
import co.electriccoin.zcash.ui.screen.home.resyncing.WalletResyncingMessageState
import co.electriccoin.zcash.ui.screen.home.shieldfunds.ShieldFundsMessage
import co.electriccoin.zcash.ui.screen.home.shieldfunds.ShieldFundsMessageState
import co.electriccoin.zcash.ui.screen.home.syncing.WalletSyncingMessage
import co.electriccoin.zcash.ui.screen.home.syncing.WalletSyncingMessageState
import co.electriccoin.zcash.ui.screen.home.tor.EnableTorMessage
import co.electriccoin.zcash.ui.screen.home.tor.EnableTorMessageState
import co.electriccoin.zcash.ui.screen.home.updating.WalletUpdatingMessage
import co.electriccoin.zcash.ui.screen.home.updating.WalletUpdatingMessageState
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Suppress("MagicNumber", "CyclomaticComplexMethod")
@Composable
fun HomeMessage(
    state: HomeMessageState?,
    modifier: Modifier = Modifier
) {
    var normalizedState: HomeMessageState? by remember { mutableStateOf(state) }
    var isVisible by remember { mutableStateOf(state != null) }
    val bottomCornerSize by animateDpAsState(
        targetValue = if (isVisible) BOTTOM_CUTOUT_HEIGHT_DP.dp else 0.dp,
        animationSpec = animationSpec()
    )
    val bottomHeight by animateDpAsState(
        targetValue = if (isVisible) BOTTOM_CUTOUT_HEIGHT_DP.dp else TOP_CUTOUT_HEIGHT_DP.dp,
        animationSpec = animationSpec(delay = if (isVisible) null else 250.milliseconds)
    )
    val elevation by animateDpAsState(
        targetValue = if (isVisible) 2.dp else 0.dp,
        animationSpec = elevationAnimationSpec(delay = if (isVisible) 450.milliseconds else null)
    )

    Box(
        modifier = modifier
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TOP_CUTOUT_HEIGHT_DP.dp)
                    .zIndex(2f)
                    .bottomOnlyShadow(
                        elevation = elevation,
                        shape =
                            RoundedCornerShape(
                                bottomStart = TOP_CUTOUT_HEIGHT_DP.dp,
                                bottomEnd = TOP_CUTOUT_HEIGHT_DP.dp
                            ),
                        backgroundColor = ZashiColors.Surfaces.bgPrimary
                    ),
        )
        AnimatedVisibility(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .zIndex(0f),
            visible = isVisible,
            enter =
                expandIn(
                    animationSpec = animationSpec(),
                    expandFrom = Alignment.TopEnd
                ),
            exit =
                shrinkOut(
                    animationSpec = animationSpec(),
                    shrinkTowards = Alignment.TopEnd
                )
        ) {
            val contentPadding = PaddingValues(top = TOP_CUTOUT_HEIGHT_DP.dp, bottom = BOTTOM_CUTOUT_HEIGHT_DP.dp)
            val innerModifier = Modifier
            when (normalizedState) {
                is WalletBackupMessageState -> {
                    WalletBackupMessage(
                        innerModifier = innerModifier,
                        state = normalizedState as WalletBackupMessageState,
                        contentPadding = contentPadding
                    )
                }

                is EnableCurrencyConversionMessageState -> {
                    EnableCurrencyConversionMessage(
                        innerModifier = innerModifier,
                        state = normalizedState as EnableCurrencyConversionMessageState,
                        contentPadding = contentPadding
                    )
                }

                is ShieldFundsMessageState -> {
                    ShieldFundsMessage(
                        innerModifier = innerModifier,
                        state = normalizedState as ShieldFundsMessageState,
                        contentPadding = contentPadding
                    )
                }

                is WalletDisconnectedMessageState -> {
                    WalletDisconnectedMessage(
                        innerModifier = innerModifier,
                        state = normalizedState as WalletDisconnectedMessageState,
                        contentPadding = contentPadding
                    )
                }

                is WalletResyncingMessageState -> {
                    WalletResyncingMessage(
                        innerModifier = innerModifier,
                        state = normalizedState as WalletResyncingMessageState,
                        contentPadding = contentPadding
                    )
                }

                is WalletRestoringMessageState -> {
                    WalletRestoringMessage(
                        innerModifier = innerModifier,
                        state = normalizedState as WalletRestoringMessageState,
                        contentPadding = contentPadding
                    )
                }

                is WalletSyncingMessageState -> {
                    WalletSyncingMessage(
                        innerModifier = innerModifier,
                        state = normalizedState as WalletSyncingMessageState,
                        contentPadding = contentPadding
                    )
                }

                is WalletUpdatingMessageState -> {
                    WalletUpdatingMessage(
                        innerModifier = innerModifier,
                        state = normalizedState as WalletUpdatingMessageState,
                        contentPadding = contentPadding
                    )
                }

                is WalletErrorMessageState -> {
                    WalletErrorMessage(
                        innerModifier = innerModifier,
                        state = normalizedState as WalletErrorMessageState,
                        contentPadding = contentPadding
                    )
                }

                is CrashReportMessageState -> {
                    CrashReportMessage(
                        innerModifier = innerModifier,
                        state = normalizedState as CrashReportMessageState,
                        contentPadding = contentPadding
                    )
                }

                is EnableTorMessageState -> {
                    EnableTorMessage(
                        innerModifier = innerModifier,
                        state = normalizedState as EnableTorMessageState,
                        contentPadding = contentPadding
                    )
                }

                is ORSwapMessageState -> {
                    ORSwapMessage(
                        innerModifier = innerModifier,
                        state = normalizedState as ORSwapMessageState,
                        contentPadding = contentPadding
                    )
                }

                null -> {
                    // do nothing
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(bottomHeight)
                    .zIndex(1f)
                    .align(Alignment.BottomCenter)
                    .topOnlyShadow(
                        elevation = elevation,
                        shape = RoundedCornerShape(topStart = bottomCornerSize, topEnd = bottomCornerSize),
                        backgroundColor = ZashiColors.Surfaces.bgPrimary
                    ),
        )
    }

    LaunchedEffect(state) {
        when {
            normalizedState == null -> {
                normalizedState = state
                isVisible = state != null
            }

            state == null -> {
                isVisible = false
                delay(ANIMATION_DURATION_MS.milliseconds)
                normalizedState = null
            }

            normalizedState!!::class == state::class -> {
                normalizedState = state
                isVisible = true
            }

            else -> {
                isVisible = false
                delay(ANIMATION_DURATION_BETWEEN_MESSAGES_MS.milliseconds)
                normalizedState = state
                isVisible = true
            }
        }
    }
}

interface HomeMessageState

@Suppress("MagicNumber")
@Composable
private fun <T> animationSpec(delay: Duration? = null): TweenSpec<T> {
    val delayMs = delay?.inWholeMilliseconds?.toInt() ?: 0
    return tween(
        durationMillis = ANIMATION_DURATION_MS - delayMs,
        easing = CubicBezierEasing(0.6f, 0.1f, 0.3f, 0.9f),
        delayMillis = delayMs
    )
}

@Suppress("MagicNumber")
private fun <T> elevationAnimationSpec(delay: Duration? = null): TweenSpec<T> {
    val delayMs = delay?.inWholeMilliseconds?.toInt() ?: 0
    return tween(
        durationMillis = ELEVATION_ANIMATION_DURATION_MS - delayMs,
        easing = CubicBezierEasing(0.6f, 0.1f, 0.3f, 0.9f),
        delayMillis = delayMs
    )
}

private const val ANIMATION_DURATION_MS = 850
private const val ELEVATION_ANIMATION_DURATION_MS = 650
private const val ANIMATION_DURATION_BETWEEN_MESSAGES_MS = 1000
private const val TOP_CUTOUT_HEIGHT_DP = 32
private const val BOTTOM_CUTOUT_HEIGHT_DP = 24

private fun Modifier.bottomOnlyShadow(
    elevation: Dp,
    shape: Shape,
    backgroundColor: Color,
    clip: Boolean = elevation > 0.dp,
    ambientColor: Color = DefaultShadowColor,
    spotColor: Color = DefaultShadowColor,
): Modifier =
    this
        .drawWithCache {
            //  bottom shadow offset in Px based on elevation
            val bottomOffsetPx = elevation.toPx()
            // Adjust the size to extend the bottom by the bottom shadow offset
            val adjustedSize = Size(size.width, size.height + bottomOffsetPx)
            val outline = shape.createOutline(adjustedSize, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            onDrawWithContent {
                clipPath(path, ClipOp.Intersect) {
                    this@onDrawWithContent.drawContent()
                }
            }
        }.shadow(elevation, shape, clip, ambientColor, spotColor)
        .background(
            backgroundColor,
            shape
        )

private fun Modifier.topOnlyShadow(
    elevation: Dp,
    shape: Shape,
    backgroundColor: Color,
    clip: Boolean = elevation > 0.dp,
    ambientColor: Color = DefaultShadowColor,
    spotColor: Color = DefaultShadowColor,
): Modifier =
    this
        .drawWithCache {
            // Adjust the size to extend the bottom by the bottom shadow offset
            val adjustedSize = Size(size.width, size.height)
            val outline = shape.createOutline(adjustedSize, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            onDrawWithContent {
                clipPath(path, ClipOp.Intersect) {
                    this@onDrawWithContent.drawContent()
                }
            }
        }.shadow(elevation, shape, clip, ambientColor, spotColor)
        .background(
            backgroundColor,
            shape
        )
