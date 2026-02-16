@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.transactionprogress

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.GradientBgScaffold
import co.electriccoin.zcash.ui.design.component.OldZashiBottomBar
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.loadingImageRes
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState.Background.ERROR
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState.Background.PENDING
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState.Background.SUCCESS
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun TransactionProgressView(state: TransactionProgressState) {
    GradientBgScaffold(
        startColor =
            when (state.background) {
                null -> ZashiColors.Surfaces.bgPrimary
                SUCCESS -> ZashiColors.Utility.SuccessGreen.utilitySuccess100
                PENDING -> ZashiColors.Utility.Indigo.utilityIndigo100
                ERROR -> ZashiColors.Utility.ErrorRed.utilityError100
            },
        endColor = ZashiColors.Surfaces.bgPrimary,
        bottomBar = { BottomBar(state) },
        topBar = { TopBar(state) },
        content = {
            Content(
                state = state,
                modifier = Modifier.scaffoldPadding(it)
            )
        }
    )
}

@Composable
private fun TopBar(state: TransactionProgressState) {
    if (state.showAppBar) {
        ZashiSmallTopAppBar(
            colors =
                ZcashTheme.colors.topAppBarColors.copyColors(
                    containerColor = Color.Transparent
                ),
            navigationAction = {
                ZashiTopAppBarCloseNavigation(onBack = state.onBack)
            }
        )
    }
}

@Composable
private fun BottomBar(state: TransactionProgressState) {
    OldZashiBottomBar {
        if (state.secondaryButton != null) {
            ZashiButton(
                state = state.secondaryButton,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZashiDimensions.Spacing.spacing2xl)
            )
        }
        if (state.primaryButton != null) {
            ZashiButton(
                state = state.primaryButton,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZashiDimensions.Spacing.spacing2xl),
            )
        }
    }
}

@Suppress("MagicNumber")
@Composable
private fun Content(state: TransactionProgressState, modifier: Modifier = Modifier) {
    ConstraintLayout(modifier = modifier.fillMaxSize()) {
        val (content, spaceTop) = createRefs()

        Spacer(
            modifier =
                Modifier.constrainAs(spaceTop) {
                    width = Dimension.fillToConstraints
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(content.top)

                    height =
                        if (state.transactionIds == null) {
                            Dimension.percent(.45f)
                        } else {
                            Dimension.value(12.dp)
                        }
                }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .constrainAs(content) {
                        top.linkTo(spaceTop.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        width = Dimension.fillToConstraints
                        height = Dimension.wrapContent
                    }
        ) {
            ImageOrLoading(state.image)
            Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacing2xl))
            Text(
                fontWeight = FontWeight.SemiBold,
                style = ZashiTypography.header5,
                text = state.title.getValue(),
                color = ZashiColors.Text.textPrimary
            )
            Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingLg))
            Text(
                fontWeight = FontWeight.Normal,
                style = ZashiTypography.textSm,
                text = state.subtitle.getValue(),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = ZashiColors.Text.textPrimary
            )

            if (state.transactionIds != null) {
                Spacer(32.dp)
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.send_confirmation_multiple_trx_failure_ids_title),
                    fontWeight = FontWeight.Medium,
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Inputs.Default.label
                )
                Spacer(6.dp)
                state.transactionIds.forEachIndexed { index, item ->
                    if (index != 0) {
                        Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingMd))
                    }

                    Text(
                        text = item.getValue(),
                        maxLines = 1,
                        style = ZashiTypography.textMd,
                        color = ZashiColors.Inputs.Default.text,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    shape = RoundedCornerShape(ZashiDimensions.Radius.radiusLg),
                                    color = ZashiColors.Inputs.Default.bg
                                ).padding(
                                    horizontal = ZashiDimensions.Spacing.spacingLg,
                                    vertical = ZashiDimensions.Spacing.spacingMd
                                ),
                        overflow = TextOverflow.MiddleEllipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingXl))
            if (state.middleButton != null) {
                ZashiButton(
                    state = state.middleButton,
                    modifier = Modifier.wrapContentWidth(),
                    defaultPrimaryColors = ZashiButtonDefaults.tertiaryColors()
                )
            }
        }
    }
}

@Composable
private fun ImageOrLoading(imageResource: ImageResource) {
    when (imageResource) {
        is ImageResource.ByDrawable -> {
            Image(
                painter = painterResource(imageResource.resource),
                contentDescription = null
            )
        }

        ImageResource.Loading -> {
            val lottieRes = R.raw.send_confirmation_sending_v1 orDark R.raw.send_confirmation_sending_dark_v1
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(lottieRes))
            val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)

            LottieAnimation(
                modifier =
                    Modifier
                        .size(150.dp)
                        .graphicsLayer {
                            scaleX = LOTTIE_ANIM_SCALE
                            scaleY = LOTTIE_ANIM_SCALE
                        }.offset(y = -ZashiDimensions.Spacing.spacing2xl),
                composition = composition,
                progress = { progress },
                maintainOriginalImageBounds = true
            )
        }

        is ImageResource.DisplayString -> {
            // do nothing
        }
    }
}

private const val LOTTIE_ANIM_SCALE = 1.54f

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        TransactionProgressView(
            state =
                TransactionProgressState(
                    title = stringRes("title"),
                    subtitle = stringRes("subtitle"),
                    middleButton =
                        ButtonState(
                            text = stringRes("middle btn"),
                            onClick = { }
                        ),
                    secondaryButton =
                        ButtonState(
                            text = stringRes("secondary btn"),
                            onClick = {},
                            style = ButtonStyle.SECONDARY
                        ),
                    primaryButton =
                        ButtonState(
                            text = stringRes("primary btn"),
                            onClick = {},
                            style = ButtonStyle.PRIMARY
                        ),
                    onBack = {},
                    background = SUCCESS,
                    image = imageRes(listOf(R.drawable.ic_fist_punch, R.drawable.ic_face_star).random()),
                    transactionIds =
                        listOf(
                            stringRes("adasdasdasdasdadwq123132adasdasdasdasdadwq123132"),
                            stringRes("adasdasdasdasdadwq123132adasdasdasdasdadwq123132"),
                        ),
                    showAppBar = true
                )
        )
    }

@PreviewScreens
@Composable
private fun SendingPreview() =
    ZcashTheme {
        TransactionProgressView(
            state =
                TransactionProgressState(
                    title = stringRes("title"),
                    subtitle = stringRes("subtitle"),
                    middleButton = null,
                    secondaryButton = null,
                    primaryButton = null,
                    onBack = {},
                    background = null,
                    image = loadingImageRes(),
                )
        )
    }
