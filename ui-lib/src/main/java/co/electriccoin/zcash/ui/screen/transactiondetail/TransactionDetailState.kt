package co.electriccoin.zcash.ui.screen.transactiondetail

import androidx.compose.runtime.Immutable
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.transactiondetail.info.TransactionDetailInfoState

@Immutable
data class TransactionDetailState(
    val onBack: () -> Unit,
    val bookmarkButton: IconButtonState?,
    val header: TransactionDetailHeaderState,
    val info: TransactionDetailInfoState?,
    val errorFooter: ErrorFooter?,
    val infoFooter: StringResource?,
    val primaryButton: ButtonState?,
    val secondaryButton: ButtonState?
)

@Immutable
data class ErrorFooter(
    val title: StringResource,
    val subtitle: StringResource,
)
