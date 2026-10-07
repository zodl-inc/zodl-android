package co.electriccoin.zcash.ui.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
    val containerColor by animateColorAsState(
        if (state.isEnabled) {
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
        modifier = modifier.pressMorph(interactionSource),
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
                    ).padding(ZashiChipButtonDefaults.contentPadding),
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

object ZashiMaxChipTags {
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
