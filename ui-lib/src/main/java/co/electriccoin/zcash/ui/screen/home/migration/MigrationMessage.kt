package co.electriccoin.zcash.ui.screen.home.migration

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.home.HomeMessageWrapper

@Composable
fun MigrationMessage(
    contentPadding: PaddingValues,
    state: MigrationMessageState,
    innerModifier: Modifier = Modifier,
) {
    HomeMessageWrapper(
        innerModifier = innerModifier,
        contentPadding = contentPadding,
        onClick = state.onClick,
        start = {
            Image(
                painter = painterResource(R.drawable.ic_migration_coins_swap),
                contentDescription = null,
                colorFilter = ColorFilter.tint(LocalContentColor.current)
            )
        },
        title = {
            Text(if (state.isInProgress) "Migration in Progress" else "Migration Required")
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
