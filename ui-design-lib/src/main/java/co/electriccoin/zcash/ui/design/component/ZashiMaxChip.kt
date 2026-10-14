package co.electriccoin.zcash.ui.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.util.pressMorph

@Composable
fun ZashiMaxChip(
    state: SendMaxChipState,
    modifier: Modifier = Modifier,
) {
    val clickable = state.isEnabled && !state.isLoading
    val interactionSource = remember { MutableInteractionSource() }
    // Loading is a tertiary icon button (Figma 9096:3324 / 9043:408): default tertiary colors, square, spinner only.
    val containerColor by animateColorAsState(
        if (state.isEnabled || state.isLoading) {
            ZashiColors.Btns.Tertiary.btnTertiaryBg
        } else {
            ZashiColors.Btns.Tertiary.btnTertiaryBgDisabled
        }
    )
    val contentColor by animateColorAsState(
        if (state.isEnabled) {
            ZashiColors.Btns.Tertiary.btnTertiaryFg
        } else {
            ZashiColors.Btns.Tertiary.btnTertiaryFgDisabled
        }
    )
    Surface(
        modifier = modifier.pressMorph(interactionSource).testTag(ZashiMaxChipTags.CHIP),
        shape = ZashiChipButtonDefaults.shape,
        color = containerColor,
    ) {
        Box(
            modifier =
                Modifier
                    .clickable(
                        onClick = state.onClick,
                        enabled = clickable,
                        interactionSource = interactionSource,
                        indication = ripple()
                    ).padding(
                        if (state.isLoading) {
                            ZashiMaxChipDefaults.loadingPadding
                        } else {
                            ZashiChipButtonDefaults.contentPadding
                        }
                    ),
            contentAlignment = Alignment.Center
        ) {
            if (state.isLoading) {
                LottieProgress(modifier = Modifier.testTag(ZashiMaxChipTags.PROGRESS))
            } else {
                Text(
                    text = stringResource(R.string.general_max),
                    style = ZashiChipButtonDefaults.textStyle,
                    color = contentColor
                )
            }
        }
    }
}

@Immutable
data class SendMaxChipState(
    val isEnabled: Boolean,
    val isLoading: Boolean,
    val onClick: () -> Unit,
)

object ZashiMaxChipDefaults {
    /** 16dp spinner + 10dp on each side = 36dp square, the same height as the text chip (20dp line + 2 x 8dp). */
    val loadingPadding: PaddingValues
        get() = PaddingValues(10.dp)
}

object ZashiMaxChipTags {
    const val CHIP = "zashi_max_chip"
    const val PROGRESS = "zashi_max_chip_progress"
}

private class SendMaxChipStatePreviewProvider : PreviewParameterProvider<SendMaxChipState> {
    override val values =
        sequenceOf(
            SendMaxChipState(isEnabled = true, isLoading = false, onClick = {}),
            SendMaxChipState(isEnabled = false, isLoading = false, onClick = {}),
            SendMaxChipState(isEnabled = true, isLoading = true, onClick = {}),
        )
}

@PreviewScreens
@Composable
private fun ZashiMaxChipPreview(
    @PreviewParameter(SendMaxChipStatePreviewProvider::class) state: SendMaxChipState
) = ZcashTheme {
    BlankSurface {
        ZashiMaxChip(state = state, modifier = Modifier.padding(ZcashTheme.dimens.spacingDefault))
    }
}
