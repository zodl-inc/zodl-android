package co.electriccoin.zcash.ui.screen.transactionprogress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.ExactInputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ExactOutputSwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.RegularTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.SendTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ShieldTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.SwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.datasource.Zip321TransactionProposal
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.repository.SubmitProposalState
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.GetProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveTransactionSubmitStateUseCase
import co.electriccoin.zcash.ui.common.usecase.SendEmailUseCase
import co.electriccoin.zcash.ui.common.usecase.ViewTransactionDetailAfterSuccessfulProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.ViewTransactionsAfterSuccessfulProposalUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.loadingImageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState.Background.ERROR
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState.Background.PENDING
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressState.Background.SUCCESS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionProgressVM(
    observeTransactionProposal: ObserveProposalUseCase,
    observeTransactionSubmitState: ObserveTransactionSubmitStateUseCase,
    private val getTransactionProposal: GetProposalUseCase,
    private val viewTransactionsAfterSuccessfulProposal: ViewTransactionsAfterSuccessfulProposalUseCase,
    private val viewTransactionDetailAfterSuccessfulProposal: ViewTransactionDetailAfterSuccessfulProposalUseCase,
    private val sendEmail: SendEmailUseCase,
    private val copyToClipboard: CopyToClipboardUseCase
) : ViewModel() {
    val state: StateFlow<TransactionProgressState?> =
        combine(observeTransactionProposal(), observeTransactionSubmitState()) { proposal, submitState ->
            when (submitState) {
                null, SubmitProposalState.Submitting -> createSendingState(proposal)
                is SubmitProposalState.Result -> {
                    when (submitState.submitResult) {
                        is SubmitResult.Success -> createSuccessState(proposal, submitState.submitResult)
                        is SubmitResult.NonResubmittableError ->
                            createNonResubmittableErrorState(proposal, submitState.submitResult)

                        is SubmitResult.GrpcFailure ->
                            createPendingState(proposal, submitState.submitResult)

                        is SubmitResult.Partial -> createFundsStuckState(submitState.submitResult)
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun createFundsStuckState(result: SubmitResult.Partial): TransactionProgressState =
        TransactionProgressState(
            onBack = ::onCloseClick,
            background = ERROR,
            title = stringRes(R.string.send_confirmation_failure_title),
            subtitle = stringRes(R.string.send_confirmation_multiple_trx_failure_text),
            middleButton = null,
            secondaryButton =
                ButtonState(
                    text = stringRes(R.string.send_confirmation_multiple_trx_failure_copy_button),
                    onClick = { copyToClipboard(value = result.txIds.joinToString()) },
                    style = ButtonStyle.TERTIARY,
                    icon = R.drawable.ic_copy
                ),
            primaryButton =
                ButtonState(
                    text = stringRes(R.string.send_confirmation_multiple_trx_failure_report_button),
                    onClick = { viewModelScope.launch { sendEmail(result) } },
                    style = ButtonStyle.PRIMARY
                ),
            image = imageRes(R.drawable.ic_multi_trx_send_failed),
            transactionIds = result.txIds.map { stringRes(it) },
            showAppBar = true
        )

    private suspend fun createSendingState(proposal: TransactionProposal?) =
        TransactionProgressState(
            onBack = {
                // do nothing
            },
            subtitle =
                when (proposal) {
                    is ShieldTransactionProposal -> stringRes(R.string.send_confirmation_sending_subtitle_transparent)
                    is SwapTransactionProposal ->
                        stringRes(R.string.send_confirmation_swapping_subtitle_transparent)

                    else -> stringRes(R.string.send_confirmation_sending_subtitle, getAddressAbbreviated())
                },
            title =
                if (proposal is ShieldTransactionProposal) {
                    stringRes(R.string.send_confirmation_sending_title_transparent)
                } else {
                    stringRes(R.string.send_confirmation_sending_title)
                },
            middleButton = null,
            primaryButton = null,
            secondaryButton = null,
            background = null,
            image = loadingImageRes()
        )

    private suspend fun createSuccessState(
        proposal: TransactionProposal,
        result: SubmitResult.Success
    ): TransactionProgressState {
        val txId = result.txIds.lastOrNull()
        return TransactionProgressState(
            onBack = ::onCloseClick,
            background = SUCCESS,
            title =
                if (proposal is ShieldTransactionProposal) {
                    stringRes(R.string.send_confirmation_success_title_transparent)
                } else {
                    stringRes(R.string.send_confirmation_success_title)
                },
            subtitle =
                when (proposal) {
                    is ShieldTransactionProposal ->
                        stringRes(R.string.send_confirmation_success_subtitle_transparent)

                    is ExactInputSwapTransactionProposal ->
                        stringRes(R.string.send_confirmation_success_swap_subtitle)

                    is ExactOutputSwapTransactionProposal ->
                        stringRes(R.string.send_confirmation_success_cross_chain_subtitle)

                    else ->
                        stringRes(R.string.send_confirmation_success_subtitle, getAddressAbbreviated())
                },
            middleButton =
                when (proposal) {
                    is ExactInputSwapTransactionProposal,
                    is ExactOutputSwapTransactionProposal -> null

                    else -> {
                        if (txId != null) {
                            ButtonState(
                                text = stringRes(R.string.send_confirmation_success_view_trx),
                                onClick = { onViewTransactionDetailClick(txId) }
                            )
                        } else {
                            null
                        }
                    }
                },
            secondaryButton =
                when (proposal) {
                    is ExactInputSwapTransactionProposal,
                    is ExactOutputSwapTransactionProposal ->
                        ButtonState(
                            text = stringRes(R.string.send_confirmation_success_btn_close),
                            onClick = ::onCloseClick,
                            style = ButtonStyle.SECONDARY
                        )

                    else -> null
                },
            primaryButton =
                when (proposal) {
                    is ExactInputSwapTransactionProposal,
                    is ExactOutputSwapTransactionProposal ->
                        if (txId != null) {
                            ButtonState(
                                text = stringRes(R.string.send_confirmation_success_btn_check_status),
                                onClick = { onViewTransactionDetailClick(txId) },
                                style = ButtonStyle.PRIMARY
                            )
                        } else {
                            null
                        }

                    else ->
                        ButtonState(
                            text = stringRes(R.string.send_confirmation_success_btn_close),
                            onClick = ::onCloseClick,
                            style = ButtonStyle.TERTIARY
                        )
                },
            image = imageRes(listOf(R.drawable.ic_fist_punch, R.drawable.ic_face_star).random())
        )
    }

    @Suppress("CyclomaticComplexMethod")
    private fun createPendingState(
        proposal: TransactionProposal,
        result: SubmitResult.GrpcFailure
    ): TransactionProgressState {
        val txId = result.txIds.lastOrNull()
        return TransactionProgressState(
            onBack = ::onCloseClick,
            background = PENDING,
            title =
                when (proposal) {
                    is Zip321TransactionProposal,
                    is RegularTransactionProposal ->
                        stringRes(R.string.send_confirmation_pending_transaction_title)

                    is ExactInputSwapTransactionProposal ->
                        stringRes(R.string.send_confirmation_pending_swap_title)

                    is ExactOutputSwapTransactionProposal ->
                        stringRes(R.string.send_confirmation_pending_payment_title)

                    is ShieldTransactionProposal ->
                        stringRes(R.string.send_confirmation_pending_shielding_title)
                },
            subtitle =
                when (proposal) {
                    is Zip321TransactionProposal,
                    is RegularTransactionProposal ->
                        stringRes(R.string.send_confirmation_pending_transaction_subtitle)

                    is ExactInputSwapTransactionProposal,
                    is ExactOutputSwapTransactionProposal ->
                        stringRes(R.string.send_confirmation_pending_swap_subtitle)

                    is ShieldTransactionProposal ->
                        stringRes(R.string.send_confirmation_pending_shielding_subtitle)
                },
            middleButton =
                when (proposal) {
                    is ExactInputSwapTransactionProposal,
                    is ExactOutputSwapTransactionProposal -> null

                    else -> {
                        if (txId != null) {
                            ButtonState(
                                text = stringRes(R.string.send_confirmation_success_view_trx),
                                onClick = { onViewTransactionDetailClick(txId) }
                            )
                        } else {
                            null
                        }
                    }
                },
            secondaryButton =
                when (proposal) {
                    is ExactInputSwapTransactionProposal,
                    is ExactOutputSwapTransactionProposal ->
                        ButtonState(
                            text = stringRes(R.string.send_confirmation_success_btn_close),
                            onClick = ::onCloseClick,
                            style = ButtonStyle.SECONDARY
                        )

                    else -> null
                },
            primaryButton =
                when (proposal) {
                    is ExactInputSwapTransactionProposal,
                    is ExactOutputSwapTransactionProposal ->
                        if (txId != null) {
                            ButtonState(
                                text = stringRes(R.string.send_confirmation_success_btn_check_status),
                                onClick = { onViewTransactionDetailClick(txId) },
                                style = ButtonStyle.PRIMARY
                            )
                        } else {
                            null
                        }

                    else ->
                        ButtonState(
                            text = stringRes(R.string.send_confirmation_success_btn_close),
                            onClick = ::onCloseClick,
                            style = ButtonStyle.TERTIARY
                        )
                },
            image = imageRes(listOf(R.drawable.ic_fist_punch, R.drawable.ic_face_star).random())
        )
    }

    private fun createNonResubmittableErrorState(
        proposal: TransactionProposal,
        result: SubmitResult.NonResubmittableError
    ): TransactionProgressState =
        TransactionProgressState(
            onBack = ::onCloseClick,
            background = ERROR,
            title =
                if (proposal is ShieldTransactionProposal) {
                    stringRes(R.string.send_confirmation_failure_title_transparent)
                } else {
                    stringRes(R.string.send_confirmation_failure_title)
                },
            subtitle =
                when (proposal) {
                    is ExactInputSwapTransactionProposal ->
                        stringRes(R.string.send_confirmation_error_swap_subtitle)

                    is ExactOutputSwapTransactionProposal ->
                        stringRes(R.string.send_confirmation_error_swap_subtitle)

                    is ShieldTransactionProposal -> stringRes(R.string.send_confirmation_failure_subtitle_transparent)
                    else -> stringRes(R.string.send_confirmation_failure_subtitle)
                },
            middleButton = null,
            secondaryButton =
                ButtonState(
                    text = stringRes(R.string.send_confirmation_failure_report_button),
                    onClick = {
                        viewModelScope.launch {
                            when (result) {
                                is SubmitResult.Error -> sendEmail(result)
                                is SubmitResult.Failure -> sendEmail(result)
                            }
                        }
                    },
                    style = ButtonStyle.TERTIARY
                ),
            primaryButton =
                ButtonState(
                    text = stringRes(R.string.send_confirmation_success_btn_close),
                    onClick = ::onCloseClick,
                    style = ButtonStyle.PRIMARY
                ),
            image =
                imageRes(
                    listOf(
                        R.drawable.ic_skull,
                        R.drawable.ic_cloud_eyes,
                        R.drawable.ic_face_horns
                    ).random()
                )
        )

    private suspend fun getAddressAbbreviated(): StringResource {
        val address = (getTransactionProposal() as? SendTransactionProposal)?.destination?.address
        return address?.let { stringResByAddress(it) } ?: stringRes("")
    }

    private fun onCloseClick() = viewTransactionsAfterSuccessfulProposal()

    private fun onViewTransactionDetailClick(txId: String) = viewTransactionDetailAfterSuccessfulProposal(txId)
}
