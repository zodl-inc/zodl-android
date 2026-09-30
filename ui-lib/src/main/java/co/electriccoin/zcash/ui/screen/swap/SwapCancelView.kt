package co.electriccoin.zcash.ui.screen.swap

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiInScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwapCancelView(state: SwapCancelState?) {
    ZashiInScreenModalBottomSheet(
        state = state
    ) { innerState ->
        Error(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, false),
            state = innerState
        )
    }
}

@Composable
private fun Error(
    state: SwapCancelState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
    ) {
        if (state.icon is ImageResource.ByDrawable) {
            Image(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                painter = painterResource(state.icon.resource),
                contentDescription = null
            )
        }
        Spacer(8.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.title.getValue(),
            textAlign = TextAlign.Center,
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary
        )
        Spacer(8.dp)
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = state.subtitle.getValue(),
            textAlign = TextAlign.Center,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(32.dp)
        ZashiButton(
            state = state.negativeButton,
            modifier = Modifier.fillMaxWidth(),
            defaultPrimaryColors = ZashiButtonDefaults.destructive1Colors()
        )
        Spacer(8.dp)
        ZashiButton(
            state = state.positiveButton,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
