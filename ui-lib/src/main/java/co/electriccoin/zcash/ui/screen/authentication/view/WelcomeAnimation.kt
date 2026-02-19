@file:Suppress("MatchingDeclarationName")

package co.electriccoin.zcash.ui.screen.authentication.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.screenHeight
import co.electriccoin.zcash.ui.screen.authentication.view.AnimationConstants.ANIMATION_DURATION
import co.electriccoin.zcash.ui.screen.authentication.view.AnimationConstants.INITIAL_DELAY
import co.electriccoin.zcash.ui.screen.authentication.view.AnimationConstants.WELCOME_ANIM_TEST_TAG
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object AnimationConstants {
    const val ANIMATION_DURATION = 700
    const val INITIAL_DELAY = 1000
    const val WELCOME_ANIM_TEST_TAG = "WELCOME_ANIM_TEST_TAG"

    fun together() = (ANIMATION_DURATION + INITIAL_DELAY).toLong()

    fun durationOnly() = (ANIMATION_DURATION).toLong()
}

// TODO [#1002]: Welcome screen animation masking
// TODO [#1002]: https://github.com/Electric-Coin-Company/zashi-android/issues/1002

@Composable
fun WelcomeAnimationAutostart(
    showAuthLogo: Boolean,
    onRetry: (() -> Unit),
    modifier: Modifier = Modifier,
    delay: Duration = INITIAL_DELAY.milliseconds,
) {
    var currentAnimationState by remember { mutableStateOf(true) }

    WelcomeScreenView(
        showAuthLogo = showAuthLogo,
        animationState = currentAnimationState,
        onRetry = onRetry,
        modifier = modifier.testTag(WELCOME_ANIM_TEST_TAG)
    )

    // Let's start the animation automatically in case e.g. authentication is not involved
    LaunchedEffect(key1 = currentAnimationState) {
        delay(delay)
        currentAnimationState = false
    }
}

@Composable
@Suppress("LongMethod")
fun WelcomeScreenView(
    animationState: Boolean,
    showAuthLogo: Boolean,
    onRetry: (() -> Unit),
    modifier: Modifier = Modifier,
) {
    val screenHeight = screenHeight()

    Column(
        modifier =
            modifier.then(
                Modifier
                    .verticalScroll(
                        state = rememberScrollState(),
                        enabled = false
                    ).wrapContentSize()
            )
    ) {
        AnimatedVisibility(
            visible = animationState,
            exit =
                slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec =
                        tween(
                            durationMillis = ANIMATION_DURATION,
                            easing = FastOutLinearInEasing
                        )
                ),
        ) {
            Box(modifier = Modifier.wrapContentSize()) {
                Column(modifier = Modifier.wrapContentSize()) {
                    Image(
                        painter = ColorPainter(ZcashTheme.colors.welcomeAnimationColor),
                        contentScale = ContentScale.FillBounds,
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .height(screenHeight.overallScreenHeight()),
                        contentDescription = null
                    )
                    Image(
                        painter = painterResource(id = co.electriccoin.zcash.ui.design.R.drawable.chart_line),
                        contentScale = ContentScale.FillBounds,
                        colorFilter = ColorFilter.tint(color = ZcashTheme.colors.welcomeAnimationColor),
                        contentDescription = null,
                    )
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .height(screenHeight.overallScreenHeight()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.fillMaxHeight(LOGO_RELATIVE_LOCATION))

                    Image(
                        modifier = Modifier.height(48.dp),
                        painter = painterResource(id = co.electriccoin.zcash.ui.design.R.drawable.logo_with_hi),
                        contentDescription = null,
                    )

                    if (showAuthLogo) {
                        Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingXl))
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxHeight(AUTH_FAILED_WIDGET_RELATIVE_LOCATION)
                                    .padding(horizontal = ZashiDimensions.Spacing.spacing3xl),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_auth_key),
                                contentDescription =
                                    stringResource(
                                        id = R.string.authentication_failed_welcome_icon_cont_desc,
                                        stringResource(R.string.app_name)
                                    ),
                                modifier =
                                    Modifier.clickable {
                                        onRetry()
                                    }
                            )

                            Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingXl))

                            Text(
                                stringResource(id = R.string.authentication_failed_welcome_title),
                                style = ZashiTypography.textXl,
                                fontWeight = FontWeight.SemiBold,
                                color = ZashiColors.NoTheme.welcomeText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingXl))

                            Text(
                                stringResource(id = R.string.authentication_failed_welcome_subtitle),
                                style = ZashiTypography.textSm,
                                color = ZashiColors.NoTheme.welcomeText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingXl))
                    }
                }
            }
        }
    }
}

private const val AUTH_FAILED_WIDGET_RELATIVE_LOCATION = 0.65f
private const val LOGO_RELATIVE_LOCATION = 0.4f

@PreviewScreens
@Composable
private fun WelcomeScreenPreview() {
    ZcashTheme {
        WelcomeAnimationAutostart(false, {})
    }
}
