@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.exportvk.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.spackle.AndroidApiVersion
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.compose.SecureScreen
import co.electriccoin.zcash.ui.common.compose.shouldSecureScreen
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.QrCodeDefaults
import co.electriccoin.zcash.ui.design.component.QrState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiHiddenContentOverlay
import co.electriccoin.zcash.ui.design.component.ZashiInfoText
import co.electriccoin.zcash.ui.design.component.ZashiQr
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.ZodlTabLayout
import co.electriccoin.zcash.ui.design.component.blurCompat
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedFooter
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding

private val CONTENT_TOP_SPACING = 28.dp

private val CONTENT_BLOCK_SHAPE = RoundedCornerShape(24.dp)

private val QR_SIZE = 232.dp

private val QR_CONTENT_PADDING = 16.dp

/**
 * Without render-effect blur both tabs show the same reveal overlay while hidden, so the key block takes the
 * QR block's exact height there and switching tabs does not move it.
 */
private val QR_BLOCK_HEIGHT = QR_SIZE + QR_CONTENT_PADDING * 2

private val BLUR_RADIUS = 16.5.dp

@Composable
internal fun VKDetailView(state: VKDetailState) {
    if (shouldSecureScreen) {
        SecureScreen()
    }

    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ZashiSmallTopAppBar(
                title = stringResource(R.string.exportViewingKey_topBar),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState),
                navigationAction = {
                    ZashiTopAppBarBackNavigation(onBack = state.onBack)
                },
                colors =
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    ),
            )
        },
        bottomBar = {
            Footer(
                state = state,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedFooter(hazeState)
                        .padding(horizontal = ZashiDimensions.Spacing.spacing3xl)
            )
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(
                            paddingValues = paddingValues,
                            top = paddingValues.calculateTopPadding() + CONTENT_TOP_SPACING
                        )
            ) {
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.header6,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary,
                )
                Spacer(8.dp)
                Text(
                    text = state.subtitle.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(24.dp)
                ZodlTabLayout(
                    items = state.tabs,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(16.dp)
                ContentBlock(
                    content = state.content,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(24.dp)
                Spacer(1f)
                ZashiInfoText(
                    text = state.disclaimer.getValue(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.Top,
                )
            }
        }
    }
}

@Composable
private fun Footer(
    state: VKDetailState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.secondaryButton,
            defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(),
        )
        Spacer(8.dp)
        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            state = state.primaryButton,
            defaultPrimaryColors = ZashiButtonDefaults.primaryColors(),
        )
        Spacer(ZashiDimensions.Spacing.spacing3xl)
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
}

@Composable
private fun ContentBlock(
    content: VKContentState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CONTENT_BLOCK_SHAPE,
        color = ZashiColors.Surfaces.bgSecondary
    ) {
        AnimatedContent(
            targetState = content,
            contentKey = { it::class },
            transitionSpec = {
                if (!AndroidApiVersion.isAtLeastS && !initialState.isRevealed && !targetState.isRevealed) {
                    EnterTransition.None togetherWith ExitTransition.None using SizeTransform { _, _ -> snap() }
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            contentAlignment = Alignment.Center,
            label = "vkContent"
        ) { target ->
            when (target) {
                is VKContentState.Qr -> QrContent(content = target)
                is VKContentState.Key -> KeyContent(content = target)
            }
        }
    }
}

@Composable
private fun QrContent(content: VKContentState.Qr) {
    ZashiQr(
        state =
            QrState(
                qrData = content.data,
                contentDescription = content.contentDescription.takeUnless { content.isRevealed },
                isBlurred = !content.isRevealed,
                hiddenLabel = content.hiddenLabel
            ),
        modifier = Modifier.fillMaxWidth(),
        qrSize = QR_SIZE,
        colors =
            QrCodeDefaults.colors(
                background = Color.Unspecified,
                border = Color.Unspecified
            ),
        contentPadding = PaddingValues(QR_CONTENT_PADDING)
    )
}

@Composable
private fun KeyContent(content: VKContentState.Key) {
    val hiddenDescription = content.contentDescription.getValue()
    val blur by animateDpAsState(if (content.isRevealed) 0.dp else BLUR_RADIUS, label = "keyBlur")
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (!AndroidApiVersion.isAtLeastS && !content.isRevealed) {
                        Modifier.height(QR_BLOCK_HEIGHT)
                    } else {
                        Modifier
                    }
                ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .blurCompat(blur, BLUR_RADIUS)
                    .padding(24.dp)
                    .then(
                        if (content.isRevealed) {
                            Modifier
                        } else {
                            Modifier.clearAndSetSemantics { contentDescription = hiddenDescription }
                        }
                    ),
            text = content.data,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
            textAlign = TextAlign.Center,
        )
        ZashiHiddenContentOverlay(
            isHidden = !content.isRevealed,
            text = content.hiddenLabel.getValue(),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@PreviewScreens
@Composable
private fun HiddenQrPreview() =
    ZcashTheme {
        VKDetailView(state = VKDetailState.preview)
    }

@PreviewScreens
@Composable
private fun RevealedQrPreview() =
    ZcashTheme {
        VKDetailView(state = VKDetailState.previewRevealedQr)
    }

@PreviewScreens
@Composable
private fun HiddenKeyPreview() =
    ZcashTheme {
        VKDetailView(state = VKDetailState.previewHiddenKey)
    }

@PreviewScreens
@Composable
private fun RevealedKeyDarkPreview() =
    ZcashTheme(appearanceMode = AppearanceMode.DARK) {
        VKDetailView(state = VKDetailState.previewRevealedKey)
    }
