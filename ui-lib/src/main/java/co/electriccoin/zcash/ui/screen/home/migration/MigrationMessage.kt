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
import co.electriccoin.zcash.ui.design.component.ZashiCircularProgressIndicatorByPercent
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.HomeMessageWrapper

@Composable
fun MigrationMessage(
    contentPadding: PaddingValues,
    state: MigrationMessageState,
    innerModifier: Modifier = Modifier,
) {
    // All migration home messages use a Colors/Utility/Gray/utility-gray-700 -> utility-gray-950
    // linear gradient background, with the Btns/Ghost/btn-ghost-bg token for readable content on
    // top of it — not the shared purple gradient every other home message (Backup, ShieldFunds,
    // etc.) uses.
    val bannerBackgroundBrush = Brush.verticalGradient(
        0f to ZashiColors.Utility.Gray.utilityGray700,
        1f to ZashiColors.Utility.Gray.utilityGray950,
    )
    val bannerContentColor = ZashiColors.Btns.Ghost.btnGhostBg
    HomeMessageWrapper(
        innerModifier = innerModifier,
        contentPadding = contentPadding,
        onClick = state.onClick,
        backgroundBrush = bannerBackgroundBrush,
        contentColor = bannerContentColor,
        subtitleColor = bannerContentColor.copy(alpha = 0.7f),
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
            }
        },
        title = {
            Text(
                when (state.phase) {
                    MigrationBannerPhase.COMPLETE -> "Migration complete"
                    MigrationBannerPhase.IN_PROGRESS -> "Migration Progress"
                    MigrationBannerPhase.REQUIRED -> "Migration Required"
                }
            )
        },
        subtitle = {
            Text(state.progressLabel ?: "Move your funds to Ironwood.")
        },
        end = {
            ZashiButton(
                modifier = Modifier.height(36.dp),
                state =
                    ButtonState(
                        onClick = state.onButtonClick,
                        text = stringRes(stringResource(R.string.general_more))
                    )
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
                state = MigrationMessageState(
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
                state = MigrationMessageState(
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
                state = MigrationMessageState(
                    phase = MigrationBannerPhase.COMPLETE,
                    progressLabel = "Tap to review the details",
                    onClick = {},
                    onButtonClick = {},
                ),
            )
        }
    }
