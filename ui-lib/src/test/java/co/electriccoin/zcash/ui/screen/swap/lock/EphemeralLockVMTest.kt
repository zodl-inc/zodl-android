package co.electriccoin.zcash.ui.screen.swap.lock

import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.RegularTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.SendTransactionProposal
import co.electriccoin.zcash.ui.common.ledger.LedgerProposalPipeline
import co.electriccoin.zcash.ui.common.model.LedgerOperationUnsupportedException
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitIncreaseEphemeralGapLimitUseCase
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * [EphemeralLockVM.onSubmitClick] wraps a Ledger's not-yet-supported ephemeral-gap-limit
 * increase as a general error rather than crashing the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EphemeralLockVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun submittingOnALedgerAccountRoutesToTheGeneralErrorScreenInsteadOfCrashing() =
        runTest {
            val exception = LedgerOperationUnsupportedException()
            val submitIncreaseEphemeralGapLimit =
                mockk<SubmitIncreaseEphemeralGapLimitUseCase> {
                    coEvery { this@mockk() } throws exception
                }
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val vm =
                vm(
                    submitIncreaseEphemeralGapLimit = submitIncreaseEphemeralGapLimit,
                    navigateToError = navigateToError,
                )
            collect(vm)

            assertNotNull(vm.state.value).primaryButton.onClick()
            runCurrent()

            verify(exactly = 1) { navigateToError(ErrorArgs.General(exception), any()) }
        }

    @Test
    fun submittingSuccessfullyNeverRoutesToTheErrorScreen() =
        runTest {
            val submitIncreaseEphemeralGapLimit = mockk<SubmitIncreaseEphemeralGapLimitUseCase>(relaxed = true)
            val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
            val vm =
                vm(
                    submitIncreaseEphemeralGapLimit = submitIncreaseEphemeralGapLimit,
                    navigateToError = navigateToError,
                )
            collect(vm)

            assertNotNull(vm.state.value).primaryButton.onClick()
            runCurrent()

            coVerify(exactly = 1) { submitIncreaseEphemeralGapLimit() }
            verify(exactly = 0) { navigateToError(any(), any()) }
        }

    private suspend fun vm(
        submitIncreaseEphemeralGapLimit: SubmitIncreaseEphemeralGapLimitUseCase,
        navigateToError: NavigateToErrorUseCase,
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = EphemeralLockVM(
        observeProposal =
            mockk {
                every { filterSend() } returns flowOf(proposal())
            },
        navigationRouter = navigationRouter,
        submitIncreaseEphemeralGapLimit = submitIncreaseEphemeralGapLimit,
        zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true),
        keystoneProposalRepository = mockk<KeystoneProposalRepository>(relaxed = true),
        ledgerProposalPipeline = mockk<LedgerProposalPipeline>(relaxed = true),
        navigateToError = navigateToError,
    )

    private suspend fun proposal(): SendTransactionProposal =
        RegularTransactionProposal(
            destination = WalletAddress.Unified.new(RECIPIENT),
            amount = Zatoshi(1234L),
            memo = Memo(""),
            proposal =
                mockk<Proposal> {
                    every { totalFeeRequired() } returns Zatoshi(1000L)
                },
        )

    private fun TestScope.collect(vm: EphemeralLockVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    companion object {
        private const val RECIPIENT = "recipient"
    }
}
