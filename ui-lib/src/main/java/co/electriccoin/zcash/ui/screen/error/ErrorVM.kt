package co.electriccoin.zcash.ui.screen.error

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.usecase.OptInExchangeRateAndTorUseCase
import co.electriccoin.zcash.ui.common.usecase.SendEmailUseCase
import co.electriccoin.zcash.ui.common.viewmodel.STACKTRACE_LIMIT
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions")
class ErrorVM(
    args: ErrorArgs,
    private val navigateToErrorBottom: NavigateToErrorUseCase,
    private val navigationRouter: NavigationRouter,
    private val sendEmailUseCase: SendEmailUseCase,
    private val optInExchangeRateAndTor: OptInExchangeRateAndTorUseCase
) : ViewModel() {
    val state: StateFlow<ErrorState> = MutableStateFlow(createState(args)).asStateFlow()

    override fun onCleared() {
        navigateToErrorBottom.clear()
        super.onCleared()
    }

    private fun onBack() = navigationRouter.back()

    private fun createState(args: ErrorArgs): ErrorState =
        when (args) {
            is ErrorArgs.SyncError -> createSyncErrorState(args)
            is ErrorArgs.ShieldingError -> createShieldingErrorState(args)
            is ErrorArgs.General -> createGeneralErrorState(args)
            is ErrorArgs.ShieldingGeneralError -> createGeneralShieldingErrorState(args)
            is ErrorArgs.SynchronizerTorInitError -> createSdkSynchronizerError()
        }

    private fun createSdkSynchronizerError(): ErrorState =
        ErrorState(
            title = stringRes(R.string.error_tor_title),
            message = stringRes(R.string.error_tor_message),
            positive =
                ButtonState(
                    text = stringRes(R.string.error_tor_negative),
                    onClick = ::onDisableTorClick
                ),
            negative =
                ButtonState(
                    text = stringRes(R.string.error_tor_positive),
                    onClick = { navigationRouter.back() }
                ),
            onBack = ::onBack,
        )

    private fun createSyncErrorState(args: ErrorArgs.SyncError) =
        ErrorState(
            title = stringRes(R.string.error_sync_title),
            message = stringRes(args.synchronizerError.getStackTrace(STACKTRACE_LIMIT).orEmpty()),
            positive =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                    onClick = { navigationRouter.back() }
                ),
            negative =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_report),
                    onClick = { sendReportClick(args) }
                ),
            onBack = ::onBack,
        )

    private fun createShieldingErrorState(args: ErrorArgs.ShieldingError) =
        ErrorState(
            title = stringRes(R.string.error_shielding_title),
            message =
                when (args.error) {
                    is SubmitResult.Error,
                    is SubmitResult.GrpcFailure -> stringRes(R.string.error_shielding_message_grpc)
                    is SubmitResult.Failure ->
                        stringRes(
                            R.string.error_shielding_message,
                            stringRes(
                                buildString {
                                    appendLine("Error code: ${args.error.code}")
                                    appendLine(args.error.description ?: "Unknown error")
                                }
                            )
                        )

                    is SubmitResult.Partial ->
                        stringRes(
                            R.string.error_shielding_message,
                            args.error.statuses.joinToString()
                        )

                    is SubmitResult.Success -> stringRes("")
                },
            positive =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                    onClick = { navigationRouter.back() }
                ),
            negative =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_report),
                    onClick = { sendReportClick(args) }
                ),
            onBack = ::onBack,
        )

    private fun createGeneralErrorState(args: ErrorArgs.General) =
        ErrorState(
            title = stringRes(R.string.error_general_title),
            message =
                stringRes(
                    R.string.error_general_message,
                    stringRes(args.exception.stackTraceToString().take(STACKTRACE_LIMIT))
                ),
            positive =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                    onClick = { navigationRouter.back() }
                ),
            negative =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_report),
                    onClick = { sendReportClick(args.exception) }
                ),
            onBack = ::onBack,
        )

    private fun createGeneralShieldingErrorState(args: ErrorArgs.ShieldingGeneralError) =
        ErrorState(
            title = stringRes(R.string.error_shielding_title),
            message =
                stringRes(
                    R.string.error_shielding_message,
                    stringRes(args.exception.stackTraceToString().take(STACKTRACE_LIMIT))
                ),
            positive =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                    onClick = { navigationRouter.back() }
                ),
            negative =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_report),
                    onClick = { sendReportClick(args.exception) }
                ),
            onBack = ::onBack,
        )

    private fun sendReportClick(args: ErrorArgs.ShieldingError) =
        viewModelScope.launch {
            withContext(NonCancellable) {
                navigationRouter.back()
                when (args.error) {
                    is SubmitResult.Failure -> sendEmailUseCase(args.error)
                    is SubmitResult.GrpcFailure -> sendEmailUseCase(args.error)
                    is SubmitResult.Partial -> sendEmailUseCase(args.error)
                    is SubmitResult.Error -> sendEmailUseCase(args.error)
                    is SubmitResult.Success -> {
                        // do nothing
                    }
                }
            }
        }

    private fun sendReportClick(args: ErrorArgs.SyncError) =
        viewModelScope.launch {
            withContext(NonCancellable) {
                navigationRouter.back()
                sendEmailUseCase(args.synchronizerError)
            }
        }

    private fun sendReportClick(exception: Exception) =
        viewModelScope.launch {
            withContext(NonCancellable) {
                navigationRouter.back()
                sendEmailUseCase(exception)
            }
        }

    private fun onDisableTorClick() = viewModelScope.launch { optInExchangeRateAndTor(false) }
}
