package co.electriccoin.zcash.ui.screen.reviewtransaction

import androidx.annotation.DrawableRes
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ChipButtonState
import co.electriccoin.zcash.ui.design.component.SwapQuoteHeaderState
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource

data class ReviewTransactionState(
    val title: StringResource,
    val items: List<ReviewTransactionItemState>,
    val primaryButton: ButtonState,
    val onBack: () -> Unit,
    // Non-null while the Orchard-spend warning sheet is shown on entry (spec §8 bottom sheet):
    // the send would spend Orchard notes before migration, leaking the amount at the turnstile.
    val orchardWarningSheet: ZashiConfirmationState? = null,
)

sealed interface ReviewTransactionItemState

data class AmountState(
    val title: StringResource?,
    val amount: Zatoshi,
    val exchangeRate: ExchangeRateState,
) : ReviewTransactionItemState

data class ExactOutputQuoteState(
    val state: SwapQuoteHeaderState
) : ReviewTransactionItemState

data class ReceiverState(
    val title: StringResource,
    val name: StringResource?,
    val address: StyledStringResource,
) : ReviewTransactionItemState

data class ReceiverExpandedState(
    val title: StringResource,
    val name: StringResource?,
    val address: StyledStringResource,
    val showButton: ChipButtonState,
    val saveButton: ChipButtonState?
) : ReviewTransactionItemState

/** The account sending, with its [icon] in its colour. */
data class SenderState(
    val title: StringResource,
    val icon: ImageResource,
    val name: StringResource
) : ReviewTransactionItemState

data class FinancialInfoState(
    val title: StringResource,
    val amount: Zatoshi,
) : ReviewTransactionItemState

data class SimpleListItemState(
    val title: StyledStringResource,
    val text: StyledStringResource,
    val subtext: StyledStringResource?
) : ReviewTransactionItemState

data class MessageState(
    val title: StringResource,
    val message: StringResource
) : ReviewTransactionItemState

data class MessagePlaceholderState(
    @field:DrawableRes val icon: Int,
    val title: StringResource,
    val message: StringResource,
) : ReviewTransactionItemState

// Spec §8 "Orchard Privacy Warning on Regular Send": shown when Proposal.usesOrchardInputs() is
// true — see ReviewTransactionVM.createState(). Distinct from the migration flow's own
// PrivacyDisclaimerCard usages (MigrationSetupScreen/MigrationCompleteScreen) despite sharing that
// same Composable — this one is scoped entirely to the ordinary, non-migration send flow.
data class OrchardPrivacyWarningState(
    val title: String,
    val body: String,
) : ReviewTransactionItemState

data object DividerState : ReviewTransactionItemState
