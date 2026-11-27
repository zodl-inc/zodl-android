package co.electriccoin.zcash.ui.common.usecase

import androidx.biometric.BiometricManager
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricRequest
import co.electriccoin.zcash.ui.common.repository.BiometricResult
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.design.util.StringResource
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthorizeVotingSubmissionUseCaseTest {
    @Test
    fun nonKeystoneSubmissionRequestsDeviceAuthentication() =
        runBlocking {
            val biometricRepository = FakeBiometricRepository()

            val result = AuthorizeVotingSubmissionUseCase(biometricRepository)(isKeystone = false)

            assertEquals(VotingSubmissionAuthorizationResult.Authorized, result)
            val request = requireNotNull(biometricRepository.requests.single())
            val message = assertIs<StringResource.ByResource>(request.message)
            assertEquals(R.string.authentication_system_ui_subtitle, message.resource)
            val useCaseName = assertIs<StringResource.ByResource>(message.args.single())
            assertEquals(R.string.authentication_use_case_vote_submission, useCaseName.resource)
        }

    @Test
    fun keystoneSubmissionDoesNotRequestDeviceAuthentication() =
        runBlocking {
            val biometricRepository = FakeBiometricRepository()

            val result = AuthorizeVotingSubmissionUseCase(biometricRepository)(isKeystone = true)

            assertEquals(VotingSubmissionAuthorizationResult.Authorized, result)
            assertEquals(emptyList(), biometricRepository.requests)
        }

    @Test
    fun biometricCancellationReturnsCancelled() =
        runBlocking {
            val biometricRepository = FakeBiometricRepository(result = FakeBiometricResult.Cancelled)

            val result = AuthorizeVotingSubmissionUseCase(biometricRepository)(isKeystone = false)

            assertEquals(VotingSubmissionAuthorizationResult.Cancelled, result)
        }

    @Test
    fun biometricFailureReturnsFailed() =
        runBlocking {
            val biometricRepository = FakeBiometricRepository(result = FakeBiometricResult.Failed)

            val result = AuthorizeVotingSubmissionUseCase(biometricRepository)(isKeystone = false)

            assertEquals(VotingSubmissionAuthorizationResult.Failed, result)
        }
}

private class FakeBiometricRepository(
    private val result: FakeBiometricResult = FakeBiometricResult.Success
) : BiometricRepository {
    val requests = mutableListOf<BiometricRequest>()

    override val allowedAuthenticators: Int =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override fun onBiometricResult(result: BiometricResult) = Unit

    override suspend fun requestBiometrics(request: BiometricRequest) {
        requests += request
        when (result) {
            FakeBiometricResult.Success -> Unit
            FakeBiometricResult.Cancelled -> throw BiometricsCancelledException()
            FakeBiometricResult.Failed -> throw BiometricsFailureException()
        }
    }
}

private enum class FakeBiometricResult {
    Success,
    Cancelled,
    Failed
}
