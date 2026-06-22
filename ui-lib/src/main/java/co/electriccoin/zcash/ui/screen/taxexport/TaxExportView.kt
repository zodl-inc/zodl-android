package co.electriccoin.zcash.ui.screen.taxexport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreenSizes
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun TaxExportView(state: TaxExportState) {
    Scaffold(
        topBar = {
            TaxExportAppBar(
                state = state,
            )
        },
    ) { paddingValues ->
        Content(
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(paddingValues)
                    .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun TaxExportAppBar(state: TaxExportState) {
    ZashiSmallTopAppBar(
        title = stringResource(R.string.tax_export_title),
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = state.onBack)
        },
    )
}

@Composable
private fun Content(
    state: TaxExportState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.tax_export_subtitle),
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = state.text.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary
        )

        Spacer(Modifier.weight(1f))

        ZashiButton(
            state = state.exportButton,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@PreviewScreenSizes
@Composable
private fun ExportPrivateDataPreview() =
    ZcashTheme {
        TaxExportView(
            state =
                TaxExportState(
                    onBack = {},
                    exportButton =
                        ButtonState(
                            text = stringRes(R.string.taxExport_download),
                            onClick = {}
                        ),
                    text =
                        stringRes(
                            R.string.tax_export_message,
                            stringResource(R.string.zashi_wallet_name)
                        )
                ),
        )
    }
