package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository
import co.electriccoin.zcash.ui.common.repository.SubmitProposalState
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A selected [LedgerAccount] reads and writes the Ledger proposal repository - exactly as a Keystone
 * or Zashi account reads its own - across every use case that dispatches on account type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerProposalDispatchTest {
    @Test
    fun observeProposalReadsTheLedgerRepositoryForALedgerAccount() =
        runTest {
            val proposal = mockk<TransactionProposal>()
            val ledgerProposalRepository =
                mockk<LedgerProposalRepository>(relaxed = true) {
                    every { transactionProposal } returns MutableStateFlow(proposal)
                }
            val useCase =
                ObserveProposalUseCase(
                    keystoneProposalRepository = mockk<KeystoneProposalRepository>(relaxed = true),
                    ledgerProposalRepository = ledgerProposalRepository,
                    zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true),
                    accountDataSource =
                        mockk<AccountDataSource> {
                            every { selectedAccount } returns flowOf(mockk<LedgerAccount>())
                        },
                )

            assertEquals(proposal, useCase().first())
        }

    @Test
    fun getProposalReadsTheLedgerRepositoryForALedgerAccount() =
        runTest {
            val proposal = mockk<TransactionProposal>()
            val ledgerProposalRepository =
                mockk<LedgerProposalRepository>(relaxed = true) {
                    coEvery { getTransactionProposal() } returns proposal
                }
            val useCase =
                GetProposalUseCase(
                    keystoneProposalRepository = mockk<KeystoneProposalRepository>(relaxed = true),
                    ledgerProposalRepository = ledgerProposalRepository,
                    zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true),
                    accountDataSource =
                        mockk<AccountDataSource> {
                            coEvery { getSelectedAccount() } returns mockk<LedgerAccount>()
                        },
                )

            assertEquals(proposal, useCase())
        }

    @Test
    fun observeTransactionSubmitStateReadsTheLedgerRepositoryForALedgerAccount() =
        runTest {
            val state = SubmitProposalState.Submitting
            val ledgerProposalRepository =
                mockk<LedgerProposalRepository>(relaxed = true) {
                    every { submitState } returns MutableStateFlow<SubmitProposalState?>(state)
                }
            val useCase =
                ObserveTransactionSubmitStateUseCase(
                    keystoneProposalRepository = mockk<KeystoneProposalRepository>(relaxed = true),
                    ledgerProposalRepository = ledgerProposalRepository,
                    zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true),
                    accountDataSource =
                        mockk<AccountDataSource> {
                            every { selectedAccount } returns flowOf(mockk<LedgerAccount>())
                        },
                )

            assertEquals(state, useCase().first())
        }
}
