package co.electriccoin.zcash.ui.screen.home.migration

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiCircularProgressIndicatorByPercent
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiLightColors
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.HomeMessageWrapper
import co.electriccoin.zcash.ui.design.R as DesignR

@Composable
fun MigrationMessage(
    contentPadding: PaddingValues,
    state: MigrationMessageState,
    innerModifier: Modifier = Modifier,
) {
    // All migration home messages use a Colors/Utility/Gray/utility-gray-700 -> utility-gray-950
    // linear gradient background, with the Btns/Ghost/btn-ghost-bg token for readable content on
    // top of it — not the shared purple gradient every other home message (Backup, ShieldFunds,
    // etc.) uses. Like that shared purple gradient (which HomeMessageWrapper pins to
    // ZashiLightColors so the banner branding doesn't flip with the system theme), this must read
    // from ZashiLightColors rather than the theme-reactive ZashiColors — otherwise in dark mode the
    // gradient collapses to Shark 200 -> Shark 25 (light gray -> white), which reads as a flat,
    // barely-there card instead of a visible gradient.
    val bannerBackgroundBrush =
        Brush.verticalGradient(
            0f to ZashiLightColors.Utility.Gray.utilityGray700,
            1f to ZashiLightColors.Utility.Gray.utilityGray950,
        )
    val bannerContentColor = ZashiLightColors.Btns.Ghost.btnGhostBg
    HomeMessageWrapper(
        innerModifier = innerModifier,
        contentPadding = contentPadding,
        onClick = state.onClick,
        backgroundBrush = bannerBackgroundBrush,
        contentColor = bannerContentColor,
        subtitleColor = bannerContentColor.copy(alpha = 0.7f),
        // The progress ring's track must match this banner's gray palette too — otherwise it
        // inherits HomeMessageWrapper's default purple track, which clashes once migration is
        // in progress and the ring is actually visible on screen.
        progressTrackColor = ZashiLightColors.Utility.Gray.utilityGray500,
        start = {
            when (state.phase) {
                MigrationBannerPhase.IN_PROGRESS -> {
                    ZashiCircularProgressIndicatorByPercent(
                        modifier = Modifier.size(20.dp),
                        progressPercent = state.progressPercent ?: 0f,
                    )
                }

                MigrationBannerPhase.COMPLETE -> {
                    Image(
                        painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(LocalContentColor.current)
                    )
                }

                MigrationBannerPhase.REQUIRED -> {
                    Image(
                        painter = painterResource(R.drawable.ic_migration_coins_swap),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(LocalContentColor.current)
                    )
                }

                MigrationBannerPhase.READY_TO_SEND -> {
                    Image(
                        painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(LocalContentColor.current)
                    )
                }

                MigrationBannerPhase.ATTENTION -> {
                    Image(
                        painter = painterResource(R.drawable.ic_alert_circle),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(LocalContentColor.current)
                    )
                }
            }
        },
        title = {
            Text(
                state.title ?: when (state.phase) {
                    MigrationBannerPhase.COMPLETE -> {
                        stringRes(DesignR.string.migrationHome_completeTitle).getValue()
                    }

                    MigrationBannerPhase.IN_PROGRESS -> {
                        stringRes(DesignR.string.migration_common_progressTitle).getValue()
                    }

                    MigrationBannerPhase.REQUIRED -> {
                        stringRes(DesignR.string.migrationHome_requiredTitle).getValue()
                    }

                    MigrationBannerPhase.READY_TO_SEND -> {
                        stringRes(DesignR.string.migrationHome_readyToSendTitle).getValue()
                    }

                    MigrationBannerPhase.ATTENTION -> {
                        stringRes(DesignR.string.migrationHome_attentionTitle).getValue()
                    }
                }
            )
        },
        subtitle = {
            Text(state.progressLabel ?: stringRes(DesignR.string.migrationHome_defaultSubtitle).getValue())
        },
        end = {
            ZashiButton(
                modifier = Modifier.height(36.dp),
                state =
                    ButtonState(
                        onClick = state.onButtonClick,
                        text = stringRes(stringResource(R.string.general_more))
                    ),
                // MOB-1620: pinned to ZashiLightColors like the rest of this banner (see the
                // banner-background comment above) — the un-pinned default read from the
                // theme-reactive ZashiColors, so the button flipped black in light mode and
                // blended into this always-dark banner instead of standing out white.
                defaultPrimaryColors = ZashiButtonDefaults.secondaryColors(source = ZashiLightColors),
            )
        },
    )
}

@PreviewScreens
@Composable
private fun PreviewRequired() =
    ZcashTheme {
        BlankSurface {
            MigrationMessage(
                contentPadding = PaddingValues(),
                state =
                    MigrationMessageState(
                        phase = MigrationBannerPhase.REQUIRED,
                        progressLabel = null,
                        onClick = {},
                        onButtonClick = {},
                    ),
            )
        }
    }

@PreviewScreens
@Composable
private fun PreviewInProgress() =
    ZcashTheme {
        BlankSurface {
            MigrationMessage(
                contentPadding = PaddingValues(),
                state =
                    MigrationMessageState(
                        phase = MigrationBannerPhase.IN_PROGRESS,
                        progressLabel = "2 of 5 transfers done ~ 40% complete",
                        progressPercent = 40f,
                        onClick = {},
                        onButtonClick = {},
                    ),
            )
        }
    }

@PreviewScreens
@Composable
private fun PreviewComplete() =
    ZcashTheme {
        BlankSurface {
            MigrationMessage(
                contentPadding = PaddingValues(),
                state =
                    MigrationMessageState(
                        phase = MigrationBannerPhase.COMPLETE,
                        progressLabel = "Tap to review the details",
                        onClick = {},
                        onButtonClick = {},
                    ),
            )
        }
    }

@PreviewScreens
@Composable
private fun PreviewCompleteResidue() =
    ZcashTheme {
        BlankSurface {
            MigrationMessage(
                contentPadding = PaddingValues(),
                state =
                    MigrationMessageState(
                        phase = MigrationBannerPhase.COMPLETE,
                        title = "0.008 ZEC left in Orchard",
                        progressLabel = "Tap to decide what happens to it",
                        onClick = {},
                        onButtonClick = {},
                    ),
            )
        }
    }

@PreviewScreens
@Composable
private fun PreviewReadyToSend() =
    ZcashTheme {
        BlankSurface {
            MigrationMessage(
                contentPadding = PaddingValues(),
                state =
                    MigrationMessageState(
                        phase = MigrationBannerPhase.READY_TO_SEND,
                        progressLabel = "Transfer 3 is ready to send",
                        onClick = {},
                        onButtonClick = {},
                    ),
            )
        }
    }

@PreviewScreens
@Composable
private fun PreviewAttentionTransferExpired() =
    ZcashTheme {
        BlankSurface {
            MigrationMessage(
                contentPadding = PaddingValues(),
                state =
                    MigrationMessageState(
                        phase = MigrationBannerPhase.ATTENTION,
                        title = "Transfer 3–5 expired",
                        progressLabel = "Tap to review the details",
                        onClick = {},
                        onButtonClick = {},
                    ),
            )
        }
    }

@PreviewScreens
@Composable
private fun PreviewAttentionPlanUpdate() =
    ZcashTheme {
        BlankSurface {
            MigrationMessage(
                contentPadding = PaddingValues(),
                state =
                    MigrationMessageState(
                        phase = MigrationBannerPhase.ATTENTION,
                        title = "Update migration plan",
                        progressLabel = "Tap to review the details",
                        onClick = {},
                        onButtonClick = {},
                    ),
            )
        }
    }
