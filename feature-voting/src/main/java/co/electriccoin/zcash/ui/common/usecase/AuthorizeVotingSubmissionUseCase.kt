package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRequest
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.design.util.stringRes

class AuthorizeVotingSubmissionUseCase(
    private val biometricRepository: BiometricRepository
) {
    suspend operator fun invoke(isKeystone: Boolean): VotingSubmissionAuthorizationResult {
        if (isKeystone) {
            return VotingSubmissionAuthorizationResult.Authorized
        }

        return try {
            biometricRepository.requestBiometrics(
                BiometricRequest(
                    message =
                        stringRes(
                            R.string.authentication_system_ui_subtitle,
                            stringRes(R.string.authentication_use_case_vote_submission)
                        )
                )
            )
            VotingSubmissionAuthorizationResult.Authorized
        } catch (_: BiometricsCancelledException) {
            VotingSubmissionAuthorizationResult.Cancelled
        } catch (_: BiometricsFailureException) {
            VotingSubmissionAuthorizationResult.Failed
        }
    }
}

sealed interface VotingSubmissionAuthorizationResult {
    data object Authorized : VotingSubmissionAuthorizationResult

    data object Cancelled : VotingSubmissionAuthorizationResult

    data object Failed : VotingSubmissionAuthorizationResult
}
