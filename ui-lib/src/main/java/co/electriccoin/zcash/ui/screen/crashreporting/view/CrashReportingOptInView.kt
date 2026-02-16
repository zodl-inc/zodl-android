package co.electriccoin.zcash.ui.screen.crashreporting.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.screen.crashreporting.model.CrashReportingOptInState
import co.electriccoin.zcash.ui.screen.exchangerate.settings.Option

@Composable
fun CrashReportingOptIn(state: CrashReportingOptInState) {
    Scaffold { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(paddingValues)
        ) {
            Button(
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(40.dp),
                onClick = state.onBack,
                shape = RoundedCornerShape(12.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ZashiColors.Btns.Tertiary.btnTertiaryBg
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_crash_reporting_opt_in_close),
                    contentDescription = stringResource(R.string.close_navigation_content_description),
                    colorFilter = ColorFilter.tint(ZashiColors.Btns.Tertiary.btnTertiaryFg)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            CrashReportingOptInContent(state)
        }
    }
}

@Composable
fun CrashReportingOptInContent(state: CrashReportingOptInState) {
    var isOptInSelected by remember(state.isOptedIn) { mutableStateOf(state.isOptedIn) }

    val isButtonDisabled by remember(state.isOptedIn) {
        derivedStateOf {
            (state.isOptedIn && isOptInSelected) || (!state.isOptedIn && !isOptInSelected)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
    ) {
        Image(
            modifier = Modifier.height(48.dp),
            painter = painterResource(R.drawable.crash_report_detail),
            contentDescription = null
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.crash_reporting_opt_in_title),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.crash_reporting_opt_in_subtitle),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )

        CrashReportingOptInOptions(
            isOptInSelected = isOptInSelected,
            setOptInSelected = { isOptInSelected = it }
        )

        Spacer(modifier = Modifier.height(24.dp))
        Spacer(modifier = Modifier.weight(1f))

        CrashReportingOptInFooter(
            isOptInSelected = isOptInSelected,
            isSaveDisabled = isButtonDisabled,
            state = state
        )
    }
}

@Composable
fun CrashReportingOptInFooter(
    isOptInSelected: Boolean,
    isSaveDisabled: Boolean,
    state: CrashReportingOptInState
) {
    Column {
        Row {
            Image(
                painter = painterResource(R.drawable.ic_crash_reporting_opt_in_info),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ZashiColors.Text.textTertiary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.crash_reporting_opt_in_info),
                color = ZashiColors.Text.textTertiary,
                style = ZashiTypography.textXs
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        ZashiButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.crash_reporting_opt_in_save),
            onClick = { state.onSaveClicked(isOptInSelected) },
            enabled = !isSaveDisabled,
            colors = ZashiButtonDefaults.primaryColors(),
            hapticFeedbackType = HapticFeedbackType.Confirm
        )
    }
}

@Composable
fun CrashReportingOptInOptions(
    isOptInSelected: Boolean,
    setOptInSelected: (Boolean) -> Unit,
) {
    Column {
        Spacer(modifier = Modifier.height(24.dp))
        Option(
            modifier = Modifier.fillMaxWidth(),
            image = R.drawable.ic_opt_in,
            isChecked = isOptInSelected,
            title = stringResource(R.string.crash_reporting_opt_in_positive),
            subtitle = stringResource(R.string.crash_reporting_opt_in_positive_desc),
            onClick = { setOptInSelected(true) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Option(
            modifier = Modifier.fillMaxWidth(),
            image = R.drawable.ic_opt_out,
            isChecked = !isOptInSelected,
            title = stringResource(R.string.crash_reporting_opt_in_negative),
            subtitle = stringResource(R.string.crash_reporting_opt_in_negative_desc),
            onClick = { setOptInSelected(false) }
        )
    }
}

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun CrashReportingOptInPreviews() =
    ZcashTheme {
        BlankSurface {
            CrashReportingOptIn(CrashReportingOptInState.new())
        }
    }
