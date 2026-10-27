package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.ProposalDataSourceImpl
import co.electriccoin.zcash.ui.common.ledger.LedgerProposalPipeline
import co.electriccoin.zcash.ui.common.model.AccountOperationUnsupportedException
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.requireSpendable
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.EphemeralAddressRepository
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase.Zip321ParseUriValidation
import co.electriccoin.zcash.ui.common.voting.VotingSettingsEntry
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.scan.ScanArgs
import co.electriccoin.zcash.ui.screen.scan.ScanFlow
import co.electriccoin.zcash.ui.screen.swap.SwapArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * A selected [ViewOnlyAccount] holds no spend authority, so every spend chokepoint refuses it with
 * [AccountOperationUnsupportedException] of kind VIEW_ONLY — thrown where the caller handles
 * failures, routed to the error screen where the use case is the top of its own job — and none of
 * them ever reaches a proposal repository, biometrics or the send flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewOnlySpendGuardTest {
    private val viewOnly =
        ViewOnlyAccount(
            sdkAccount = Account.new(AccountUuid.new(ByteArray(16) { it.toByte() })),
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
            name = stringRes("Savings"),
            customName = "Savings",
            icon = 0,
        )

    private val accountDataSource =
        mockk<AccountDataSource> {
            coEvery { getSelectedAccount() } returns viewOnly
            every { allAccounts } returns MutableStateFlow(listOf(viewOnly))
            coEvery { requestNextShieldedAddress() } returns "u"
        }

    private val zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true)
    private val keystoneProposalRepository = mockk<KeystoneProposalRepository>(relaxed = true)
    private val ledgerProposalPipeline = mockk<LedgerProposalPipeline>(relaxed = true)
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)
    private val navigateToError = mockk<NavigateToErrorUseCase>(relaxed = true)
    private val biometricRepository = mockk<BiometricRepository>(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createProposalThrowsWithoutCreatingAProposal() =
        runTest {
            val useCase =
                CreateProposalUseCase(
                    keystoneProposalRepository = keystoneProposalRepository,
                    ledgerProposalPipeline = ledgerProposalPipeline,
                    zashiProposalRepository = zashiProposalRepository,
                    accountDataSource = accountDataSource,
                    navigationRouter = navigationRouter,
                )

            assertViewOnly(assertFailsWith { useCase(zecSend = mockk(relaxed = true), floor = false) })
            assertNoProposalCreated()
            verify(exactly = 0) { navigationRouter.forward(*anyVararg()) }
        }

    @Test
    fun zip321ScanFromHomeShowsTheUnsupportedError() =
        runTest {
            val useCase =
                OnZip321ScannedUseCase(
                    keystoneProposalRepository = keystoneProposalRepository,
                    ledgerProposalPipeline = ledgerProposalPipeline,
                    zashiProposalRepository = zashiProposalRepository,
                    accountDataSource = accountDataSource,
                    navigationRouter = navigationRouter,
                    prefillSend = mockk(relaxed = true),
                    navigateToErrorUseCase = navigateToError,
                )

            useCase(Zip321ParseUriValidation.Valid("zcash:t1", mockk(relaxed = true)), ScanArgs(ScanFlow.HOMEPAGE))

            verifyViewOnlyErrorShown()
            assertNoProposalCreated()
        }

    @Test
    fun zip321ScanFromSendShowsTheUnsupportedError() =
        runTest {
            val useCase =
                OnZip321ScannedUseCase(
                    keystoneProposalRepository = keystoneProposalRepository,
                    ledgerProposalPipeline = ledgerProposalPipeline,
                    zashiProposalRepository = zashiProposalRepository,
                    accountDataSource = accountDataSource,
                    navigationRouter = navigationRouter,
                    prefillSend = mockk(relaxed = true),
                    navigateToErrorUseCase = navigateToError,
                )

            useCase(Zip321ParseUriValidation.Valid("zcash:t1", mockk(relaxed = true)), ScanArgs(ScanFlow.SEND))

            verify(exactly = 1) { navigateToError(match { it.isViewOnlyError() }, any()) }
            assertNoProposalCreated()
        }

    @Test
    fun swapQuoteNeverBecomesAProposal() =
        runTest {
            val swapRepository =
                mockk<SwapRepository>(relaxed = true) {
                    every { quote } returns MutableStateFlow(SwapQuoteData.Success(mockk(relaxed = true)))
                }
            val useCase =
                RequestSwapQuoteUseCase(
                    navigationRouter = navigationRouter,
                    navigateToErrorUseCase = navigateToError,
                    swapRepository = swapRepository,
                    zashiProposalRepository = zashiProposalRepository,
                    keystoneProposalRepository = keystoneProposalRepository,
                    ledgerProposalPipeline = ledgerProposalPipeline,
                    accountDataSource = accountDataSource,
                    synchronizerProvider = mockk(),
                )

            useCase.requestExactInput(
                amount = BigDecimal.ONE,
                address = "address",
                selectedAsset = mockk(relaxed = true),
                slippage = BigDecimal.ONE,
                canNavigateToSwapQuote = { true },
            )

            verifyViewOnlyErrorShown()
            assertNoProposalCreated()
        }

    @Test
    fun shieldingShowsTheUnsupportedError() =
        runTest {
            val useCase =
                ShieldFundsUseCase(
                    keystoneProposalRepository = keystoneProposalRepository,
                    ledgerProposalPipeline = ledgerProposalPipeline,
                    ledgerNavigator = mockk(relaxed = true),
                    zashiProposalRepository = zashiProposalRepository,
                    navigationRouter = navigationRouter,
                    accountDataSource = accountDataSource,
                    navigateToError = navigateToError,
                    messageAvailabilityDataSource = mockk(relaxed = true),
                )

            useCase(closeCurrentScreen = true)

            verifyViewOnlyErrorShown()
            coVerify(exactly = 0) { zashiProposalRepository.createShieldProposal() }
            coVerify(exactly = 0) { keystoneProposalRepository.createShieldProposal() }
            coVerify(exactly = 0) { ledgerProposalPipeline.createShieldProposal() }
            coVerify(exactly = 0) { zashiProposalRepository.submit() }
        }

    @Test
    fun gapLimitProposalThrows() =
        runTest {
            val useCase =
                CreateIncreaseEphemeralGapLimitProposalUseCase(
                    keystoneProposalRepository = keystoneProposalRepository,
                    zashiProposalRepository = zashiProposalRepository,
                    accountDataSource = accountDataSource,
                    ephemeralAddressRepository =
                        mockk<EphemeralAddressRepository> {
                            coEvery { get() } returns mockk { every { address } returns "t1Ephemeral" }
                        },
                )

            assertViewOnly(assertFailsWith { useCase() })
            assertNoProposalCreated()
        }

    @Test
    fun gapLimitSubmitThrows() =
        runTest {
            val useCase =
                SubmitIncreaseEphemeralGapLimitUseCase(
                    navigationRouter = navigationRouter,
                    accountDataSource = accountDataSource,
                    zashiProposalRepository = zashiProposalRepository,
                    biometricRepository = biometricRepository,
                    ephemeralAddressRepository = mockk(relaxed = true),
                )

            assertViewOnly(assertFailsWith { useCase() })
            coVerify(exactly = 0) { zashiProposalRepository.submit() }
        }

    @Test
    fun submitShowsTheUnsupportedErrorWithoutReadingOrSubmittingAProposal() =
        runTest {
            val useCase =
                SubmitProposalUseCase(
                    navigationRouter = navigationRouter,
                    accountDataSource = accountDataSource,
                    zashiProposalRepository = zashiProposalRepository,
                    keystoneProposalRepository = keystoneProposalRepository,
                    ledgerProposalPipeline = ledgerProposalPipeline,
                    ledgerNavigator = mockk(relaxed = true),
                    biometricRepository = biometricRepository,
                    swapRepository = mockk(relaxed = true),
                    metadataRepository = mockk(relaxed = true),
                    processSwapTransaction = mockk(relaxed = true),
                    prefillSend = mockk(relaxed = true),
                    navigateToError = navigateToError,
                )

            useCase()

            verifyViewOnlyErrorShown()
            coVerify(exactly = 0) { zashiProposalRepository.getTransactionProposal() }
            coVerify(exactly = 0) { zashiProposalRepository.submit() }
            verify(exactly = 0) { navigationRouter.replace(*anyVararg()) }
        }

    @Test
    fun getProposalThrows() =
        runTest {
            val useCase =
                GetProposalUseCase(
                    keystoneProposalRepository = keystoneProposalRepository,
                    ledgerProposalPipeline = ledgerProposalPipeline,
                    zashiProposalRepository = zashiProposalRepository,
                    accountDataSource = accountDataSource,
                )

            assertViewOnly(assertFailsWith { useCase() })
        }

    @Test
    fun votingShowsTheUnsupportedError() =
        runTest {
            val votingSettingsEntry = mockk<VotingSettingsEntry>(relaxed = true)
            val useCase =
                NavigateToVotingUseCase(
                    getSelectedWalletAccount = GetSelectedWalletAccountUseCase(accountDataSource),
                    hasSeenHowToVote = mockk(relaxed = true),
                    hasSeenHowToVoteKeystone = mockk(relaxed = true),
                    votingSettingsEntry = votingSettingsEntry,
                    navigateToError = navigateToError,
                )

            useCase()

            verifyViewOnlyErrorShown()
            verify(exactly = 0) { votingSettingsEntry.navigateToCoinholderPolling() }
            verify(exactly = 0) { votingSettingsEntry.navigateToHowToVote() }
        }

    @Test
    fun sendAndPayEntriesShowTheUnsupportedErrorInsteadOfTheirFlow() =
        runTest {
            val swapRepository = mockk<SwapRepository>(relaxed = true)

            NavigateToSendUseCase(
                navigationRouter,
                mockk(relaxed = true),
                RejectIfSelectedAccountCannotSpendUseCase(accountDataSource, navigateToError)
            )()
            NavigateToNearPayUseCase(
                swapRepository,
                navigationRouter,
                RejectIfSelectedAccountCannotSpendUseCase(accountDataSource, navigateToError)
            )()

            verify(exactly = 2) { navigateToError(match { it.isViewOnlyError() }, any()) }
            verify(exactly = 0) { navigationRouter.forward(*anyVararg()) }
            verify(exactly = 0) { swapRepository.requestRefreshAssets() }
        }

    @Test
    fun theSwapEntryOpensBecauseSwappingIntoTheAccountSpendsNothingFromIt() {
        val swapRepository = mockk<SwapRepository>(relaxed = true)

        NavigateToSwapUseCase(swapRepository, navigationRouter)()

        verify(exactly = 0) { navigateToError(any(), any()) }
        verify { swapRepository.requestRefreshAssets() }
        verify { navigationRouter.forward(SwapArgs) }
    }

    @Test
    fun anAddressScannedFromHomeAndSendAgainShowTheUnsupportedErrorInsteadOfTheSendForm() =
        runTest {
            val prefillSend = mockk<PrefillSendUseCase>(relaxed = true)

            OnAddressScannedUseCase(
                navigationRouter,
                prefillSend,
                RejectIfSelectedAccountCannotSpendUseCase(accountDataSource, navigateToError)
            )(
                address = "t1address",
                addressType = AddressType.Transparent,
                scanArgs = ScanArgs(ScanFlow.HOMEPAGE),
            )
            SendTransactionAgainUseCase(
                prefillSend,
                navigationRouter,
                RejectIfSelectedAccountCannotSpendUseCase(accountDataSource, navigateToError)
            )(mockk())

            verify(exactly = 2) { navigateToError(match { it.isViewOnlyError() }, any()) }
            verify(exactly = 0) { navigationRouter.replace(*anyVararg()) }
            verify(exactly = 0) { navigationRouter.forward(*anyVararg()) }
            verify(exactly = 0) { prefillSend.requestFromTransactionDetail(any()) }
        }

    @Test
    fun theProposalDataSourceRefusesTheAccountBeforeTheSdkIsAsked() =
        runTest {
            val synchronizerProvider = mockk<SynchronizerProvider>()
            val dataSource =
                ProposalDataSourceImpl(
                    synchronizerProvider = synchronizerProvider,
                    lightWalletEndpointProvider = mockk(),
                    isServerSelectionAutomaticProvider = mockk(),
                    persistableWalletProvider = mockk(),
                )

            assertViewOnly(assertFailsWith { dataSource.createProposal(viewOnly, mockk(relaxed = true)) })
            assertViewOnly(assertFailsWith { dataSource.createShieldProposal(viewOnly) })
            assertViewOnly(assertFailsWith { dataSource.createZip321Proposal(viewOnly, "zcash:t1") })
            assertViewOnly(assertFailsWith { dataSource.createPcztFromProposal(viewOnly, mockk()) })
            coVerify(exactly = 0) { synchronizerProvider.getSynchronizer() }
        }

    @Test
    fun theProposalDataSourceRefusesSwapProposalsBeforeTheSdkIsAsked() =
        runTest {
            val synchronizerProvider = mockk<SynchronizerProvider>()
            val dataSource =
                ProposalDataSourceImpl(
                    synchronizerProvider = synchronizerProvider,
                    lightWalletEndpointProvider = mockk(),
                    isServerSelectionAutomaticProvider = mockk(),
                    persistableWalletProvider = mockk(),
                )

            assertViewOnly(
                assertFailsWith { dataSource.createExactInputProposal(viewOnly, mockk(relaxed = true), mockk()) }
            )
            assertViewOnly(
                assertFailsWith { dataSource.createExactOutputProposal(viewOnly, mockk(relaxed = true), mockk()) }
            )
            coVerify(exactly = 0) { synchronizerProvider.getSynchronizer() }
        }

    @Test
    fun requireSpendableReturnsEverySpendableAccountUnchanged() {
        val zodl = EditAccountFixtures.zodl()
        val keystone = EditAccountFixtures.keystone()
        val ledger = EditAccountFixtures.ledger()

        assertSame(zodl, zodl.requireSpendable())
        assertSame(keystone, keystone.requireSpendable())
        assertSame(ledger, ledger.requireSpendable())
    }

    @Test
    fun requireSpendableRefusesOnlyTheViewOnlyAccount() {
        assertViewOnly(assertFailsWith { viewOnly.requireSpendable() })
    }

    private fun assertNoProposalCreated() {
        coVerify(exactly = 0) { zashiProposalRepository.createProposal(any()) }
        coVerify(exactly = 0) { zashiProposalRepository.createZip321Proposal(any()) }
        coVerify(exactly = 0) { zashiProposalRepository.createExactInputSwapProposal(any(), any()) }
        coVerify(exactly = 0) { keystoneProposalRepository.createProposal(any()) }
        coVerify(exactly = 0) { keystoneProposalRepository.createZip321Proposal(any()) }
        coVerify(exactly = 0) { ledgerProposalPipeline.createProposal(any()) }
        coVerify(exactly = 0) { ledgerProposalPipeline.createZip321Proposal(any()) }
    }

    private fun verifyViewOnlyErrorShown() {
        verify(exactly = 1) { navigateToError(match { it.isViewOnlyError() }, any()) }
    }

    private fun ErrorArgs.isViewOnlyError() =
        ((this as? ErrorArgs.General)?.exception as? AccountOperationUnsupportedException)?.kind ==
            AccountOperationUnsupportedException.Kind.VIEW_ONLY

    private fun assertViewOnly(exception: AccountOperationUnsupportedException) {
        assertEquals(AccountOperationUnsupportedException.Kind.VIEW_ONLY, exception.kind)
    }
}
