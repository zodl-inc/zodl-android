package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.SubmitProposalState
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Keystone vs Zashi submission routing (MOB-1145): a Keystone-signed submission surfaces the
 * Keystone repository's submit state - including a resubmittable pending result and a partial result
 * (whose support data must be preserved) - while a Zashi submission uses the Zashi repository.
 */
class ObserveTransactionSubmitStateUseCaseTest {
    private val zashiSuccess = SubmitProposalState.Result(SubmitResult.Success(txIds = listOf("z")))

    @Test
    fun keystoneAccountObservesKeystonePendingResult() =
        runTest {
            val keystonePending =
                SubmitProposalState.Result(
                    SubmitResult.GrpcFailure(txIds = listOf("ks"), reason = SubmitResult.GrpcFailure.Reason.TIMEOUT)
                )

            val useCase = useCase(account = mockk<KeystoneAccount>(), keystone = keystonePending, zashi = zashiSuccess)

            assertEquals(keystonePending, useCase().first())
        }

    @Test
    fun keystoneAccountObservesKeystonePartialResult() =
        runTest {
            val keystonePartial =
                SubmitProposalState.Result(
                    SubmitResult.Partial(txIds = listOf("a", "b"), statuses = listOf("success", "notAttempted"))
                )

            val useCase = useCase(account = mockk<KeystoneAccount>(), keystone = keystonePartial, zashi = zashiSuccess)

            assertEquals(keystonePartial, useCase().first())
        }

    @Test
    fun zashiAccountObservesZashiResult() =
        runTest {
            val keystonePending = SubmitProposalState.Result(SubmitResult.GrpcFailure(txIds = listOf("ks")))

            val useCase = useCase(account = mockk<ZashiAccount>(), keystone = keystonePending, zashi = zashiSuccess)

            assertEquals(zashiSuccess, useCase().first())
        }

    private fun useCase(
        account: WalletAccount,
        keystone: SubmitProposalState?,
        zashi: SubmitProposalState?
    ) = ObserveTransactionSubmitStateUseCase(
        keystoneProposalRepository =
            mockk<KeystoneProposalRepository> { every { submitState } returns MutableStateFlow(keystone) },
        zashiProposalRepository =
            mockk<ZashiProposalRepository> { every { submitState } returns MutableStateFlow(zashi) },
        accountDataSource =
            mockk<AccountDataSource> { every { selectedAccount } returns flowOf(account) }
    )
}
