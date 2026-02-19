package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRequest
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.common.repository.EphemeralAddressRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SubmitIncreaseEphemeralGapLimitUseCase(
    private val navigationRouter: NavigationRouter,
    private val accountDataSource: AccountDataSource,
    private val zashiProposalRepository: ZashiProposalRepository,
    private val biometricRepository: BiometricRepository,
    private val ephemeralAddressRepository: EphemeralAddressRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    suspend operator fun invoke() {
        try {
            biometricRepository.requestBiometrics(
                request =
                    BiometricRequest(
                        message =
                            stringRes(
                                R.string.authentication_system_ui_subtitle,
                                stringRes(R.string.authentication_use_case_send_funds)
                            )
                    )
            )
            when (accountDataSource.getSelectedAccount()) {
                is KeystoneAccount -> navigationRouter.replace(SignKeystoneTransactionArgs)
                is ZashiAccount -> {
                    submitZashiProposal()
                    navigationRouter.back()
                }
            }
        } catch (_: BiometricsFailureException) {
            // do nothing
        } catch (_: BiometricsCancelledException) {
            // do nothing
        }
    }

    private fun submitZashiProposal() {
        scope.launch {
            try {
                val result = zashiProposalRepository.submit()
                invalidateEphemeralAddress(result)
                zashiProposalRepository.clear()
            } catch (_: Exception) {
                // do nothing
            }
        }
    }

    private suspend fun invalidateEphemeralAddress(result: SubmitResult) {
        when (result) {
            is SubmitResult.Error,
            is SubmitResult.Failure,
            is SubmitResult.GrpcFailure,
            is SubmitResult.Success -> ephemeralAddressRepository.invalidate()

            is SubmitResult.Partial -> {
                // do nothing
            }
        }
    }
}
