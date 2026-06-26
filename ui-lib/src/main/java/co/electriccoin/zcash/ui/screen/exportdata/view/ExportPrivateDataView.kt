package co.electriccoin.zcash.ui.screen.exportdata.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiCheckbox
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreenSizes
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun ExportPrivateData(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAgree: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(
        topBar = {
            ExportPrivateDataTopAppBar(
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        ExportPrivateDataContent(
            onAgree = onAgree,
            onConfirm = onConfirm,
            modifier =
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(paddingValues)
                    .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun ExportPrivateDataTopAppBar(
    onBack: () -> Unit
) {
    ZashiSmallTopAppBar(
        title = stringResource(R.string.privateDataConsent_screenTitle),
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = onBack)
        },
    )
}

@Composable
private fun ExportPrivateDataContent(
    onAgree: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.privateDataConsent_title),
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary
        )

        Spacer(Modifier.height(ZashiDimensions.Spacing.spacingLg))

        Text(
            modifier = Modifier.testTag(ExportPrivateDataScreenTag.WARNING_TEXT_TAG),
            text = stringResource(R.string.export_data_text),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary
        )

        Spacer(Modifier.weight(1f))

        val checkedState = rememberSaveable { mutableStateOf(false) }
        ZashiCheckbox(
            modifier = Modifier.testTag(ExportPrivateDataScreenTag.AGREE_CHECKBOX_TAG),
            isChecked = checkedState.value,
            onClick = {
                val new = checkedState.value.not()
                checkedState.value = new
                onAgree(new)
            },
            text = stringRes(R.string.privateDataConsent_confirmation),
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingLarge))

        ZashiButton(
            onClick = onConfirm,
            text = stringResource(R.string.settings_exportPrivateData),
            enabled = checkedState.value,
            modifier = Modifier.fillMaxWidth(),
            hapticFeedbackType = HapticFeedbackType.Confirm
        )
    }
}

@PreviewScreenSizes
@Composable
private fun ExportPrivateDataPreview() =
    ZcashTheme {
        ExportPrivateData(
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onAgree = {},
            onConfirm = {},
        )
    }
