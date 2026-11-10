package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.WalletAddress
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.RegularTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.SendTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.SwapTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TransactionProposal
import co.electriccoin.zcash.ui.common.ledger.LedgerNavigator
import co.electriccoin.zcash.ui.common.ledger.LedgerProposalPipeline
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.BiometricsCancelledException
import co.electriccoin.zcash.ui.common.repository.BiometricsFailureException
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.common.zns.LookUpZnsRecipientUseCase
import co.electriccoin.zcash.ui.common.zns.ZnsConfirmation
import co.electriccoin.zcash.ui.common.zns.ZnsName
import co.electriccoin.zcash.ui.common.zns.ZnsSendRecipient
import co.electriccoin.zcash.ui.common.zns.ZnsSendRecipientRepository
import co.electriccoin.zcash.ui.common.zns.ZnsSendStopped
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionArgs
import co.electriccoin.zcash.ui.screen.transactionprogress.TransactionProgressArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SubmitProposalUseCase] is gated by a biometric prompt and then branches on account type:
 * - a failed/cancelled biometric does nothing;
 * - Keystone accounts navigate to the PCZT signing flow (no clear, no submit here);
 * - Zashi accounts clear swap state, submit the proposal off-thread, then navigate to progress.
 *
 * Swap proposals additionally record the destination asset in history, and a successful Zashi submit
 * of a swap proposal kicks off [ProcessSwapTransactionUseCase]. The off-thread submit always clears
 * the send prefill (even when it throws).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubmitProposalUseCaseTest {
    @Test
    fun biometricsFailureDoesNothing() =
        runTest {
            val fx = useCase()
            coEvery { fx.biometricRepository.requestBiometrics(any()) } throws BiometricsFailureException()

            fx.useCase()

            coVerify(exactly = 0) { fx.accountDataSource.getSelectedAccount() }
            coVerify(exactly = 0) { fx.zashiProposalRepository.submit() }
            verify(exactly = 0) { fx.navigationRouter.replace(*anyVararg()) }
        }

    @Test
    fun biometricsCancellationDoesNothing() =
        runTest {
            val fx = useCase()
            coEvery { fx.biometricRepository.requestBiometrics(any()) } throws BiometricsCancelledException()

            fx.useCase()

            coVerify(exactly = 0) { fx.accountDataSource.getSelectedAccount() }
            verify(exactly = 0) { fx.navigationRouter.replace(*anyVararg()) }
        }

    @Test
    fun keystoneSwapProposalAddsHistoryAndNavigatesToSignKeystone() =
        runTest {
            val fx = useCase()
            fx.givenKeystone(swapProposal(tokenTicker = "eth", chainTicker = "eth"))

            fx.useCase()

            verify(exactly = 1) { fx.metadataRepository.addSwapAssetToHistory("eth", "eth") }
            verify(exactly = 1) { fx.navigationRouter.replace(SignKeystoneTransactionArgs) }
            // Keystone defers submission to the PCZT flow — nothing is cleared, submitted, processed,
            // or prefill-cleared on this path.
            verify(exactly = 0) { fx.swapRepository.clear() }
            coVerify(exactly = 0) { fx.zashiProposalRepository.submit() }
            coVerify(exactly = 0) { fx.keystoneProposalRepository.submit() }
            coVerify(exactly = 0) { fx.processSwapTransaction(any(), any()) }
            verify(exactly = 0) { fx.prefillSend.clear() }
        }

    @Test
    fun keystoneNonSwapProposalNavigatesWithoutTouchingHistory() =
        runTest {
            val fx = useCase()
            fx.givenKeystone(mockk<TransactionProposal>())

            fx.useCase()

            verify(exactly = 0) { fx.metadataRepository.addSwapAssetToHistory(any(), any()) }
            verify(exactly = 1) { fx.navigationRouter.replace(SignKeystoneTransactionArgs) }
            coVerify(exactly = 0) { fx.keystoneProposalRepository.submit() }
        }

    @Test
    fun ledgerProposalOpensTheSignSheetWithoutSubmittingOrClearingSwapState() =
        runTest {
            val fx = useCase()
            fx.givenLedger(mockk<TransactionProposal>())

            fx.useCase()

            verify(exactly = 1) { fx.ledgerNavigator.forwardToSign() }
            verify(exactly = 0) { fx.swapRepository.clear() }
            verify(exactly = 0) { fx.prefillSend.clear() }
            verify(exactly = 0) { fx.navigationRouter.replace(*anyVararg()) }
        }

    @Test
    fun zashiSwapProposalAddsHistoryClearsSubmitsProcessesAndNavigates() =
        runTest {
            val fx = useCase()
            val proposal = swapProposal(tokenTicker = "eth", chainTicker = "eth")
            val submitResult = mockk<SubmitResult>(relaxed = true)
            fx.givenZashi(proposal, submitResult)

            fx.useCase()

            verify(exactly = 1) { fx.metadataRepository.addSwapAssetToHistory("eth", "eth") }
            verify(exactly = 1) { fx.swapRepository.clear() }
            coVerify(exactly = 1) { fx.zashiProposalRepository.submit() }
            coVerify(exactly = 1) { fx.processSwapTransaction(proposal, submitResult) }
            verify(exactly = 1) { fx.navigationRouter.replace(TransactionProgressArgs) }
            verify(exactly = 1) { fx.prefillSend.clear() }
            // Swap state is cleared before submitting, and navigation happens after submit is kicked off.
            coVerifyOrder {
                fx.swapRepository.clear()
                fx.zashiProposalRepository.submit()
                fx.navigationRouter.replace(TransactionProgressArgs)
            }
        }

    @Test
    fun zashiNonSwapProposalSubmitsWithoutProcessingSwap() =
        runTest {
            val fx = useCase()
            fx.givenZashi(mockk<TransactionProposal>())

            fx.useCase()

            verify(exactly = 0) { fx.metadataRepository.addSwapAssetToHistory(any(), any()) }
            verify(exactly = 1) { fx.swapRepository.clear() }
            coVerify(exactly = 1) { fx.zashiProposalRepository.submit() }
            coVerify(exactly = 0) { fx.processSwapTransaction(any(), any()) }
            verify(exactly = 1) { fx.navigationRouter.replace(TransactionProgressArgs) }
            verify(exactly = 1) { fx.prefillSend.clear() }
        }

    @Test
    fun zashiSubmitFailureStillClearsPrefillAndStillNavigates() =
        runTest {
            val fx = useCase()
            coEvery { fx.accountDataSource.getSelectedAccount() } returns spendable<ZodlAccount>()
            coEvery { fx.zashiProposalRepository.getTransactionProposal() } returns
                swapProposal(tokenTicker = "eth", chainTicker = "eth")
            coEvery { fx.zashiProposalRepository.submit() } throws RuntimeException("boom")

            fx.useCase()

            // The submit failure is swallowed: no swap processing, but the prefill is still cleared
            // (finally) and navigation to progress happens regardless of the async outcome.
            coVerify(exactly = 0) { fx.processSwapTransaction(any(), any()) }
            verify(exactly = 1) { fx.prefillSend.clear() }
            verify(exactly = 1) { fx.navigationRouter.replace(TransactionProgressArgs) }
        }

    @Test
    fun aSendToANameThatNowPointsElsewhereIsStoppedBeforeAnythingIsSigned() =
        runTest {
            ZnsConfirmation.entries.filter { it != ZnsConfirmation.UNCHANGED }.forEach { confirmation ->
                listOf<Fixtures.(TransactionProposal) -> Unit>(
                    { givenZashi(it) },
                    { givenKeystone(it) },
                    { givenLedger(it) }
                ).forEach { given ->
                    val fx = useCase()
                    fx.given(sendProposalTo(NAME_ADDRESS))
                    fx.znsSendRecipientRepository.setRecipient(ZnsSendRecipient(ALICE, NAME_ADDRESS))
                    coEvery { fx.lookUpZnsRecipient.confirm(ALICE, NAME_ADDRESS) } returns confirmation

                    fx.useCase()

                    coVerify(exactly = 1) { fx.cancelProposalFlow(clearSendForm = false) }
                    assertEquals(
                        ZnsSendStopped(ZnsSendRecipient(ALICE, NAME_ADDRESS), confirmation),
                        fx.znsSendRecipientRepository.stopped.value
                    )
                    coVerify(exactly = 0) { fx.biometricRepository.requestBiometrics(any()) }
                    coVerify(exactly = 0) { fx.zashiProposalRepository.submit() }
                    verify(exactly = 0) { fx.navigationRouter.replace(*anyVararg()) }
                    verify(exactly = 0) { fx.ledgerNavigator.forwardToSign() }
                }
            }
        }

    @Test
    fun aSendToANameThatStillPointsToThePaidAddressGoesAhead() =
        runTest {
            val fx = useCase()
            fx.givenZashi(sendProposalTo(NAME_ADDRESS))
            fx.znsSendRecipientRepository.setRecipient(ZnsSendRecipient(ALICE, NAME_ADDRESS))
            coEvery { fx.lookUpZnsRecipient.confirm(ALICE, NAME_ADDRESS) } returns ZnsConfirmation.UNCHANGED

            fx.useCase()

            coVerify(exactly = 0) { fx.cancelProposalFlow(any()) }
            coVerify(exactly = 1) { fx.zashiProposalRepository.submit() }
            verify(exactly = 1) { fx.navigationRouter.replace(TransactionProgressArgs) }
        }

    @Test
    fun aProposalForAnotherAddressIsNotCheckedAgainstTheName() =
        runTest {
            val fx = useCase()
            fx.givenZashi(sendProposalTo("u1other"))
            fx.znsSendRecipientRepository.setRecipient(ZnsSendRecipient(ALICE, NAME_ADDRESS))

            fx.useCase()

            coVerify(exactly = 0) { fx.lookUpZnsRecipient.confirm(any(), any()) }
            coVerify(exactly = 1) { fx.zashiProposalRepository.submit() }
        }

    private fun sendProposalTo(address: String): SendTransactionProposal =
        mockk<RegularTransactionProposal>(relaxed = true) {
            val walletAddress = mockk<WalletAddress>()
            every { walletAddress.address } returns address
            every { destination } returns walletAddress
        }

    private fun swapProposal(
        tokenTicker: String,
        chainTicker: String
    ): SwapTransactionProposal =
        mockk {
            every { quote } returns
                mockk {
                    every { destinationAsset } returns
                        SwapAssetTestFixture.asset(tokenTicker = tokenTicker, chainTicker = chainTicker)
                }
        }

    private fun Fixtures.givenKeystone(proposal: TransactionProposal) {
        coEvery { accountDataSource.getSelectedAccount() } returns spendable<KeystoneAccount>()
        coEvery { keystoneProposalRepository.getTransactionProposal() } returns proposal
    }

    private fun Fixtures.givenLedger(proposal: TransactionProposal) {
        coEvery { accountDataSource.getSelectedAccount() } returns spendable<LedgerAccount>()
        coEvery { ledgerProposalPipeline.getTransactionProposal() } returns proposal
    }

    private fun Fixtures.givenZashi(
        proposal: TransactionProposal,
        submitResult: SubmitResult = mockk(relaxed = true)
    ) {
        coEvery { accountDataSource.getSelectedAccount() } returns spendable<ZodlAccount>()
        coEvery { zashiProposalRepository.getTransactionProposal() } returns proposal
        coEvery { zashiProposalRepository.submit() } returns submitResult
    }

    /** Builds the use case with the off-thread submit scope pinned to the test scheduler. */
    private fun TestScope.useCase(): Fixtures {
        val fx = Fixtures()
        // The use case launches submission on its own scope; run it eagerly on the test scheduler.
        fx.useCase.scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        return fx
    }

    private class Fixtures {
        val navigationRouter = mockk<NavigationRouter>(relaxed = true)
        val accountDataSource = mockk<AccountDataSource>(relaxed = true)
        val zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true)
        val keystoneProposalRepository = mockk<KeystoneProposalRepository>(relaxed = true)
        val ledgerProposalPipeline = mockk<LedgerProposalPipeline>(relaxed = true)
        val ledgerNavigator = mockk<LedgerNavigator>(relaxed = true)
        val biometricRepository = mockk<BiometricRepository>(relaxed = true)
        val swapRepository = mockk<SwapRepository>(relaxed = true)
        val metadataRepository = mockk<MetadataRepository>(relaxed = true)
        val processSwapTransaction = mockk<ProcessSwapTransactionUseCase>(relaxed = true)
        val prefillSend = mockk<PrefillSendUseCase>(relaxed = true)
        val lookUpZnsRecipient = mockk<LookUpZnsRecipientUseCase>()
        val znsSendRecipientRepository = ZnsSendRecipientRepository()
        val cancelProposalFlow = mockk<CancelProposalFlowUseCase>(relaxed = true)
        val useCase =
            SubmitProposalUseCase(
                navigationRouter = navigationRouter,
                accountDataSource = accountDataSource,
                zashiProposalRepository = zashiProposalRepository,
                keystoneProposalRepository = keystoneProposalRepository,
                ledgerProposalPipeline = ledgerProposalPipeline,
                ledgerNavigator = ledgerNavigator,
                biometricRepository = biometricRepository,
                swapRepository = swapRepository,
                metadataRepository = metadataRepository,
                processSwapTransaction = processSwapTransaction,
                prefillSend = prefillSend,
                navigateToError = mockk(relaxed = true),
                lookUpZnsRecipient = lookUpZnsRecipient,
                znsSendRecipientRepository = znsSendRecipientRepository,
                cancelProposalFlow = cancelProposalFlow,
            )
    }

    private inline fun <reified T : WalletAccount> spendable(): T = mockk<T> { every { isSpendable } returns true }

    private companion object {
        val ALICE = checkNotNull(ZnsName.fromRegistryName("alice"))
        const val NAME_ADDRESS = "u1alice"
    }
}
