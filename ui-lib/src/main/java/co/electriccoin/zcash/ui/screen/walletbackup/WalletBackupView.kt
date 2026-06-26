package co.electriccoin.zcash.ui.screen.walletbackup

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.compose.SecureScreen
import co.electriccoin.zcash.ui.common.compose.ZashiTooltip
import co.electriccoin.zcash.ui.common.compose.ZashiTooltipBox
import co.electriccoin.zcash.ui.common.compose.shouldSecureScreen
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.SeedTextState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiSeedText
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.blurCompat
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreenSizes
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.launch

@Composable
fun WalletBackupView(state: WalletBackupState) {
    if (shouldSecureScreen) {
        SecureScreen()
    }

    Scaffold(
        topBar = {
            SeedRecoveryTopAppBar(
                state = state,
            )
        }
    ) { paddingValues ->
        SeedRecoveryMainContent(
            modifier = Modifier.scaffoldPadding(paddingValues),
            state = state,
        )
    }
}

@Composable
private fun SeedRecoveryTopAppBar(
    state: WalletBackupState,
    modifier: Modifier = Modifier,
) {
    ZashiSmallTopAppBar(
        title = stringResource(R.string.recoveryPhraseDisplay_screenTitle),
        modifier = modifier,
        navigationAction = {
            if (state.onBack != null) {
                ZashiTopAppBarBackNavigation(onBack = state.onBack)
            }
        },
        regularActions = {
            ZashiIconButton(state.info)
            Spacer(Modifier.width(20.dp))
        }
    )
}

@Composable
private fun SeedRecoveryMainContent(
    state: WalletBackupState,
    modifier: Modifier = Modifier,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .then(modifier),
    ) {
        Text(
            text = stringResource(R.string.recoveryPhraseDisplay_title),
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.header6
        )

        Spacer(8.dp)

        Text(
            text = stringResource(R.string.recoveryPhraseDisplay_description),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textSm
        )

        Spacer(20.dp)

        ZashiSeedText(modifier = Modifier.fillMaxWidth(), state = state.seed)

        Spacer(24.dp)

        BDSecret(modifier = Modifier.fillMaxWidth(), state = state.birthday)

        Spacer(24.dp)

        Spacer(1f)

        state.secondaryButton?.let {
            ZashiButton(
                state = state.secondaryButton,
                modifier = Modifier.fillMaxWidth(),
                defaultPrimaryColors = ZashiButtonDefaults.secondaryColors()
            )
            Spacer(8.dp)
        }

        ZashiButton(
            state = state.primaryButton,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BDSecret(
    state: SeedSecretState,
    modifier: Modifier = Modifier
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
    ) {
        Row(
            modifier =
                if (state.tooltip != null) {
                    Modifier
                        .clip(RoundedCornerShape(ZashiDimensions.Radius.radiusMd))
                        .clickable {
                            scope.launch {
                                if (tooltipState.isVisible) {
                                    tooltipState.dismiss()
                                } else {
                                    tooltipState.show()
                                }
                            }
                        }.padding(
                            horizontal = ZashiDimensions.Spacing.spacingXs,
                            vertical = ZashiDimensions.Spacing.spacingSm
                        )
                } else {
                    Modifier.padding(
                        horizontal = ZashiDimensions.Spacing.spacingXs,
                        vertical = ZashiDimensions.Spacing.spacingSm
                    )
                }
        ) {
            Text(
                text = state.title.getValue(),
                color = ZashiColors.Text.textPrimary,
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium
            )
            if (state.tooltip != null) {
                Spacer(2.dp)
                ZashiTooltipBox(
                    tooltip = {
                        ZashiTooltip(
                            title = state.tooltip.title,
                            message = state.tooltip.message,
                            onDismissRequest = {
                                scope.launch {
                                    tooltipState.dismiss()
                                }
                            },
                        )
                    },
                    state = tooltipState,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_zashi_tooltip),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ZashiColors.Inputs.Default.icon)
                    )
                }
            }
        }
        SecretContent(state = state)
    }
}

@Composable
private fun SecretContent(state: SeedSecretState) {
    val blur by animateDpAsState(if (state.isRevealed) 0.dp else 14.dp, label = "")
    Surface(
        modifier =
            Modifier
                .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = ZashiColors.Inputs.Filled.bg
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier =
                    Modifier
                        .blurCompat(blur, 14.dp)
                        .padding(vertical = 10.dp)
            ) {
                Text(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    textAlign = TextAlign.Start,
                    text = state.text.getValue(),
                    style = ZashiTypography.textMd,
                    fontWeight = FontWeight.Medium,
                    color = ZashiColors.Inputs.Filled.text
                )
            }
        }
    }
}

@Composable
@PreviewScreenSizes
private fun RevealedPreview() =
    ZcashTheme {
        WalletBackupView(
            state =
                WalletBackupState(
                    seed =
                        SeedTextState(
                            seed = (1..24).joinToString(" ") { "trala" } + "longer_tralala",
                            isRevealed = true
                        ),
                    birthday =
                        SeedSecretState(
                            title = stringRes("Birthday"),
                            text = stringRes(value = "asdads"),
                            isRevealed = true,
                            tooltip = SeedSecretStateTooltip(title = stringRes(""), message = stringRes("")),
                        ) {},
                    secondaryButton =
                        ButtonState(
                            text = stringRes("Text"),
                            icon = R.drawable.ic_seed_show,
                            onClick = {},
                        ),
                    primaryButton =
                        ButtonState(
                            text = stringRes("Text"),
                            icon = R.drawable.ic_seed_show,
                            onClick = {},
                        ),
                    info =
                        IconButtonState(
                            onClick = {},
                            icon = co.electriccoin.zcash.ui.design.R.drawable.ic_info
                        ),
                    onBack = {}
                )
        )
    }

@Composable
@PreviewScreenSizes
private fun HiddenPreview() =
    ZcashTheme {
        WalletBackupView(
            state =
                WalletBackupState(
                    seed =
                        SeedTextState(
                            seed = (1..24).joinToString(" ") { "trala" } + "longer_tralala",
                            isRevealed = true
                        ),
                    birthday =
                        SeedSecretState(
                            title = stringRes("Birthday"),
                            text = stringRes(value = "asdads"),
                            isRevealed = false,
                            tooltip = SeedSecretStateTooltip(title = stringRes(""), message = stringRes("")),
                        ) {},
                    secondaryButton =
                        ButtonState(
                            text = stringRes("Text"),
                            icon = R.drawable.ic_seed_show,
                            onClick = {},
                        ),
                    primaryButton =
                        ButtonState(
                            text = stringRes("Text"),
                            icon = R.drawable.ic_seed_show,
                            onClick = {},
                        ),
                    info =
                        IconButtonState(
                            onClick = {},
                            icon = co.electriccoin.zcash.ui.design.R.drawable.ic_info
                        ),
                    onBack = {}
                )
        )
    }
