package co.electriccoin.zcash.ui.screen.rebrand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun RebrandView(state: RebrandState, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(paddingValues)
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(60.dp)

                Image(
                    painter = painterResource(R.drawable.ic_rebrand),
                    contentDescription = null,
                    modifier = Modifier.height(60.dp)
                )

                Spacer(ZashiDimensions.Spacing.spacingXs)

                Text(
                    text = stringResource(R.string.rebrand_title),
                    color = ZashiColors.Text.textPrimary,
                    style = ZashiTypography.header6,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(22.dp)

                Text(
                    text = stringResource(R.string.rebrand_desc),
                    color = ZashiColors.Text.textTertiary,
                    style = ZashiTypography.textSm,
                    textAlign = TextAlign.Center
                )

                Spacer(22.dp)

                Surface(
                    color = ZashiColors.Utility.SuccessGreen.utilitySuccess50,
                    shape = RoundedCornerShape(ZashiDimensions.Spacing.spacingXl),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.rebrand_security),
                        color = ZashiColors.Utility.SuccessGreen.utilitySuccess700,
                        style = ZashiTypography.textMd,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(ZashiDimensions.Spacing.spacingXl)
                    )
                }

                Spacer(ZashiDimensions.Spacing.spacing5xl)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacingLg)
                ) {
                    Text(
                        text = stringResource(R.string.rebrand_info_title),
                        color = ZashiColors.Text.textPrimary,
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(ZashiDimensions.Spacing.spacingXs)
                    ListPoint(stringResource(R.string.rebrand_info_title_1))
                    ListPoint(stringResource(R.string.rebrand_info_title_2))
                    ListPoint(stringResource(R.string.rebrand_info_title_3))
                }
            }

            Column(modifier = Modifier.padding(vertical = 24.dp)) {
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    state = state.info,
                    defaultPrimaryColors = ZashiButtonDefaults.tertiaryColors(),
                )

                Spacer(ZashiDimensions.Spacing.spacingLg)

                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    state = state.next,
                    defaultPrimaryColors = ZashiButtonDefaults.primaryColors(),
                )
            }
        }
    }
}

@Composable
fun ListPoint(
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(ZashiDimensions.Spacing.spacingMd))

        Text(
            text = value,
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview
@Composable
private fun RebrandPreview() {
    ZcashTheme {
        RebrandView(
            RebrandState(
                ButtonState(stringRes("Learn more")),
                ButtonState(stringRes("Go to Zodl")),
            )
        )
    }
}
