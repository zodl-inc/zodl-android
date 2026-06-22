package co.electriccoin.zcash.ui.screen.warning.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarNavigation
import co.electriccoin.zcash.ui.design.component.zashiVerticalGradient
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding

@Composable
fun NotEnoughSpaceView(
    onSettings: () -> Unit,
    onSystemSettings: () -> Unit,
    spaceAvailableMegabytes: Int,
    storageSpaceRequiredGigabytes: Int,
    snackbarHostState: SnackbarHostState,
) {
    Box(
        modifier =
            Modifier.background(
                zashiVerticalGradient(ZashiColors.Utility.ErrorRed.utilityError100)
            )
    ) {
        Scaffold(
            topBar = {
                ZashiSmallTopAppBar(
                    colors = ZcashTheme.colors.topAppBarColors.copyColors(containerColor = Color.Transparent),
                    title = null,
                    subtitle = null,
                    hamburgerMenuActions = {
                        ZashiTopAppBarNavigation(
                            backContentDescriptionText =
                                stringResource(
                                    co.electriccoin.zcash.ui.design.R.string.back_navigation_content_description
                                ),
                            drawableRes = R.drawable.ic_home_more,
                            onBack = onSettings,
                            tint = ZashiColors.Text.textPrimary
                        )
                    }
                )
            },
            snackbarHost = {
                SnackbarHost(snackbarHostState)
            },
            containerColor = Color.Transparent
        ) {
            Column(modifier = Modifier.scaffoldPadding(it)) {
                @Suppress("MagicNumber")
                Spacer(Modifier.weight(.75f))
                Image(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    painter = painterResource(R.drawable.ic_not_enough_space),
                    contentDescription = null
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    text = stringResource(id = R.string.notEnoughFreeSpace_title),
                    style = ZashiTypography.header6,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text =
                        buildAnnotatedString {
                            append(
                                stringResource(
                                    R.string.not_enough_space_description_1,
                                    storageSpaceRequiredGigabytes
                                ) + " "
                            )
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(
                                    stringResource(R.string.not_enough_space_description_2, spaceAvailableMegabytes)
                                )
                            }
                            append(
                                stringResource(
                                    R.string.not_enough_space_description_3,
                                    storageSpaceRequiredGigabytes *
                                        GB_TO_MEGABYTES - spaceAvailableMegabytes
                                )
                            )
                        },
                    style = ZashiTypography.textSm,
                    textAlign = TextAlign.Center,
                    color = ZashiColors.Text.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.not_enough_space_system_settings_btn),
                    onClick = onSystemSettings,
                )
            }
        }
    }
}

@PreviewScreens
@Composable
private fun NotEnoughSpacePreview() =
    ZcashTheme {
        NotEnoughSpaceView(
            onSettings = {},
            onSystemSettings = {},
            snackbarHostState = SnackbarHostState(),
            spaceAvailableMegabytes = 300,
            storageSpaceRequiredGigabytes = 1,
        )
    }

private const val GB_TO_MEGABYTES = 1024
