package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * "Why Max isn't available" hint: an info icon, a short text and an optional trailing tertiary button
 * (Figma 9043:926 and 9043:1056). It sits right above the main button.
 */
@Composable
fun ZashiMaxHintRow(
    state: ZashiMaxHintRowState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ZashiInfoText(
            modifier = Modifier.weight(1f),
            text = state.text.getValue(),
        )
        state.button?.let { ZashiChipButton(state = it) }
    }
}

@Immutable
data class ZashiMaxHintRowState(
    val text: StringResource,
    val button: ChipButtonState? = null,
)

private class ZashiMaxHintRowStatePreviewProvider : PreviewParameterProvider<ZashiMaxHintRowState> {
    override val values =
        sequenceOf(
            ZashiMaxHintRowState(
                text = stringRes("Max uses shielded ZEC only."),
                button = ChipButtonState(text = stringRes("Shield"), onClick = {})
            ),
            ZashiMaxHintRowState(text = stringRes("Max will be available once your funds confirm."))
        )
}

@PreviewScreens
@Composable
private fun ZashiMaxHintRowPreview(
    @PreviewParameter(ZashiMaxHintRowStatePreviewProvider::class) state: ZashiMaxHintRowState
) = ZcashTheme {
    BlankSurface {
        ZashiMaxHintRow(state = state, modifier = Modifier.padding(ZcashTheme.dimens.spacingDefault))
    }
}
