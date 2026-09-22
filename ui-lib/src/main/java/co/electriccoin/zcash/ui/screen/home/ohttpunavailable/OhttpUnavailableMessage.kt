@file:Suppress("MatchingDeclarationName")

package co.electriccoin.zcash.ui.screen.home.ohttpunavailable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.HomeMessageState

data class OhttpUnavailableMessageState(
    val onChooseServer: () -> Unit
) : HomeMessageState

@Composable
internal fun OhttpUnavailableMessage(
    state: OhttpUnavailableMessageState,
    contentPadding: PaddingValues,
    innerModifier: Modifier = Modifier
) {
    Box(
        modifier =
            innerModifier
                .fillMaxWidth()
                .padding(contentPadding)
                .padding(horizontal = ZcashTheme.dimens.spacing.spacing16)
    ) {
        Column {
            Text(
                text = "Private connection unavailable",
                style = ZcashTheme.typography.textMd,
                color = ZcashTheme.colors.textPrimary
            )
            Text(
                text =
                    "Zodl couldn't reach its private (OHTTP) servers. " +
                        "You can keep waiting or choose a public server in Settings › Server. " +
                        "Public servers can see your IP address.",
                style = ZcashTheme.typography.textSm,
                color = ZcashTheme.colors.textSecondary,
                modifier = Modifier.padding(top = ZcashTheme.dimens.spacing.spacing8)
            )
            ZashiButton(
                state =
                    ButtonState(
                        text = stringRes("Choose server"),
                        onClick = state.onChooseServer
                    ),
                modifier = Modifier.padding(top = ZcashTheme.dimens.spacing.spacing12)
            )
        }
    }
}
