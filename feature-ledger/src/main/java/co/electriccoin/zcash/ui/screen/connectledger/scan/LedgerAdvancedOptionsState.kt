package co.electriccoin.zcash.ui.screen.connectledger.scan

import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * The collapsible "Advanced options" card under the device list, where the user picks which ZIP 32
 * account of the Ledger to pair. [accountIndex] carries the raw input and, while it is not a
 * valid index, an empty error that only turns the field and [hint] red.
 */
data class LedgerAdvancedOptionsState(
    val title: StringResource,
    val isExpanded: Boolean,
    val message: StringResource,
    val accountIndexLabel: StringResource,
    val accountIndex: TextFieldState,
    val hint: StringResource,
    val onToggle: () -> Unit,
) {
    companion object {
        val preview =
            LedgerAdvancedOptionsState(
                title = stringRes("Advanced options"),
                isExpanded = false,
                message =
                    stringRes(
                        "Leave this at 0 unless you used another account number on this Ledger in a different wallet."
                    ),
                accountIndexLabel = stringRes("Account index"),
                accountIndex = TextFieldState(value = stringRes("0"), onValueChange = {}),
                hint = stringRes("Enter a number from 0 to 100"),
                onToggle = {},
            )

        val previewExpanded =
            preview.copy(
                isExpanded = true,
                accountIndex = TextFieldState(value = stringRes("3"), onValueChange = {}),
            )

        val previewError =
            preview.copy(
                isExpanded = true,
                accountIndex = TextFieldState(value = stringRes("150"), error = stringRes(""), onValueChange = {}),
            )
    }
}
