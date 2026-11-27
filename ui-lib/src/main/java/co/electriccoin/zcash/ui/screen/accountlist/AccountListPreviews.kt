package co.electriccoin.zcash.ui.screen.accountlist

import androidx.compose.runtime.Composable
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@PreviewScreens
@Composable
private fun ZodlOnlyPreview() =
    ZcashTheme {
        AccountListView(state = AccountListState.preview)
    }

@PreviewScreens
@Composable
private fun KeystonePreview() =
    ZcashTheme {
        AccountListView(state = AccountListState.previewKeystone)
    }

@PreviewScreens
@Composable
private fun LedgerPreview() =
    ZcashTheme {
        AccountListView(state = AccountListState.previewLedger)
    }

@PreviewScreens
@Composable
private fun KeystoneAndLedgerPreview() =
    ZcashTheme {
        AccountListView(state = AccountListState.previewKeystoneAndLedger)
    }

@PreviewScreens
@Composable
private fun ViewOnlyPreview() =
    ZcashTheme {
        AccountListView(state = AccountListState.previewViewOnly)
    }

@PreviewScreens
@Composable
private fun ViewOnlyMultiplePreview() =
    ZcashTheme {
        AccountListView(state = AccountListState.previewViewOnlyMultiple)
    }

@PreviewScreens
@Composable
private fun KeystoneAndViewOnlyPreview() =
    ZcashTheme {
        AccountListView(state = AccountListState.previewKeystoneAndViewOnly)
    }

@PreviewScreens
@Composable
private fun AllPreview() =
    ZcashTheme {
        AccountListView(state = AccountListState.previewAll)
    }

@PreviewScreens
@Composable
private fun HardwareSelectedPreview() =
    ZcashTheme {
        AccountListView(state = AccountListState.previewHardwareSelected)
    }

@PreviewScreens
@Composable
private fun LoadingPreview() =
    ZcashTheme {
        AccountListView(state = AccountListState.previewLoading)
    }
