package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.exception.LedgerException
import cash.z.ecc.android.sdk.exception.PcztException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import cash.z.ecc.android.sdk.ledger.LedgerSigningProgress
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import co.electriccoin.zcash.ui.common.datasource.LedgerSigningDataSource
import co.electriccoin.zcash.ui.common.datasource.ProposalDataSource
import co.electriccoin.zcash.ui.common.datasource.RegularTransactionProposal
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.LedgerIssueKind
import co.electriccoin.zcash.ui.common.model.LedgerIssueRetry
import co.electriccoin.zcash.ui.common.model.LedgerSigningState
import co.electriccoin.zcash.ui.common.model.SubmitResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The Ledger signing session: scanning and device selection, the connect/sign state machine, retry
 * over a kept link versus a rescan, cancellation, and the generation guard that keeps a session that
 * already unwound from overwriting the one that replaced it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerProposalRepositoryTest {
    private val dispatcher = StandardTestDispatcher()

    private val accountDataSource = mockk<AccountDataSource>()
    private val proposalDataSource = mockk<ProposalDataSource>()
    private val ledgerDeviceDataSource = mockk<LedgerDeviceDataSource>()
    private val ledgerSigningDataSource = mockk<LedgerSigningDataSource>(relaxed = true)

    private val devices = MutableStateFlow<List<LedgerBluetoothDevice>>(emptyList())

    private val repository =
        LedgerProposalRepositoryImpl(
            accountDataSource = accountDataSource,
            proposalDataSource = proposalDataSource,
            ledgerDeviceDataSource = ledgerDeviceDataSource,
            ledgerSigningDataSource = ledgerSigningDataSource,
        ).apply { scope = CoroutineScope(dispatcher) }

    init {
        every { ledgerDeviceDataSource.observeDevices() } returns devices
    }

    @Test
    fun aLoneDeviceConnectsAfterTheSettleDelay() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            val signed = Pczt(byteArrayOf(5))
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns signed

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            coVerify(exactly = 1) { ledgerSigningDataSource.connect(match { it.identifier == "AA" }) }
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
        }

    @Test
    fun twoDevicesOffersSelectingInsteadOfAutoConnecting() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()

            devices.value = listOf(device("AA"), device("BB"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            val state = repository.signingState.value
            assertTrue(state is LedgerSigningState.Selecting)
            assertEquals(2, state.devices.size)
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any()) }
        }

    @Test
    fun selectDeviceConnectsThePickedDevice() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            val signed = Pczt(byteArrayOf(5))
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns signed

            devices.value = listOf(device("AA"), device("BB"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()
            assertTrue(repository.signingState.value is LedgerSigningState.Selecting)

            repository.selectDevice("BB")
            runCurrent()

            coVerify(exactly = 1) { ledgerSigningDataSource.connect(match { it.identifier == "BB" }) }
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
        }

    @Test
    fun twentySecondsWithoutADeviceFailsWithNoDevices() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()

            repository.startSigning()
            runCurrent()
            advanceTimeBy(21.seconds)
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.NO_DEVICES, failed.issue.kind)
        }

    @Test
    fun progressMapsToTheSigningStatesAsItArrives() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true

            val progress = slot<(LedgerSigningProgress) -> Unit>()
            val signed = Pczt(byteArrayOf(5))
            coEvery { ledgerSigningDataSource.sign(any(), any(), capture(progress)) } coAnswers {
                progress.captured(LedgerSigningProgress.IdentifyingDevice)
                assertEquals(LedgerSigningState.Streaming(0, 0), repository.signingState.value)

                progress.captured(LedgerSigningProgress.Streaming(3, 10))
                assertEquals(LedgerSigningState.Streaming(3, 10), repository.signingState.value)

                progress.captured(LedgerSigningProgress.AwaitingReviewOnDevice)
                assertEquals(LedgerSigningState.AwaitingReview, repository.signingState.value)

                progress.captured(LedgerSigningProgress.Signing)
                assertEquals(LedgerSigningState.Signing, repository.signingState.value)

                progress.captured(LedgerSigningProgress.Complete)
                assertEquals(LedgerSigningState.Signing, repository.signingState.value)

                signed
            }

            repository.startSigning()
            runCurrent()

            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
        }

    @Test
    fun submitPassesTheProofsPcztAndTheSignedPcztToSubmitTransaction() =
        runTest(dispatcher) {
            val proofsPczt = Pczt(byteArrayOf(7))
            givenPczt(ledgerAccount(), proofsPczt = proofsPczt)
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true
            val signed = Pczt(byteArrayOf(5))
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns signed
            val submitResult = mockk<SubmitResult>(relaxed = true)
            coEvery { proposalDataSource.submitTransaction(proofsPczt, signed) } returns submitResult

            repository.startSigning()
            runCurrent()
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)

            val result = repository.submit()

            assertEquals(submitResult, result)
            coVerify(exactly = 1) { proposalDataSource.submitTransaction(proofsPczt, signed) }
        }

    @Test
    fun userRejectedFailsWithSameLinkAndRetrySignsOverTheSameLinkWithoutARescan() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true
            var signCalls = 0
            val signed = Pczt(byteArrayOf(5))
            val rejected = mockk<LedgerException.UserRejected>(relaxed = true)
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } coAnswers {
                signCalls++
                if (signCalls == 1) throw rejected else signed
            }

            repository.startSigning()
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.REJECTED, failed.issue.kind)
            assertEquals(LedgerIssueRetry.SAME_LINK, failed.issue.retry)

            repository.retry()
            runCurrent()

            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any()) }
        }

    @Test
    fun disconnectedFailsWithReconnectAndRetryRescans() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            var linked = false
            every { ledgerSigningDataSource.isLinked } answers { linked }
            coEvery { ledgerSigningDataSource.connect(any()) } answers { linked = true }
            var signCalls = 0
            val signed = Pczt(byteArrayOf(5))
            val disconnected = mockk<LedgerException.Disconnected>(relaxed = true)
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } coAnswers {
                signCalls++
                if (signCalls == 1) {
                    linked = false
                    throw disconnected
                } else {
                    signed
                }
            }

            devices.value = listOf(device("AA"))
            repository.startSigning()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueRetry.RECONNECT, failed.issue.retry)

            repository.retry()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()

            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
            coVerify(exactly = 2) { ledgerSigningDataSource.connect(any()) }
        }

    @Test
    fun cancelSigningClosesTheLinkAndKeepsTheProposalPcztAndProofs() =
        runTest(dispatcher) {
            val pczt = givenPczt(ledgerAccount())
            runCurrent()

            repository.startSigning()
            runCurrent()

            repository.cancelSigning()
            runCurrent()

            assertNull(repository.signingState.value)
            coVerify(atLeast = 1) { ledgerSigningDataSource.close() }
            assertSame(pczt, repository.getProposalPCZT())
            assertNotNull(repository.transactionProposal.value)
        }

    @Test
    fun clearResetsTheProposalPcztProofsAndSigningState() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()

            repository.clear()
            runCurrent()

            assertNull(repository.transactionProposal.value)
            assertNull(repository.submitState.value)
            assertNull(repository.getProposalPCZT())
            assertNull(repository.signingState.value)
        }

    @Test
    fun submitAwaitsTheProofsJobAndSurfacesItsError() =
        runTest(dispatcher) {
            val proofsError = mockk<PcztException.AddProofsToPcztException>(relaxed = true)
            givenPczt(ledgerAccount(), proofsError = proofsError)
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } returns Pczt(byteArrayOf(5))

            repository.startSigning()
            runCurrent()
            assertEquals(LedgerSigningState.Signed, repository.signingState.value)

            val thrown =
                assertFailsWith<PcztException.AddProofsToPcztException> {
                    repository.submit()
                }
            assertSame(proofsError, thrown)
        }

    @Test
    fun anUnboundAccountFailsWithUnboundAndOffersNoRetry() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount(bound = true))
            runCurrent()
            coEvery { accountDataSource.getSelectedAccount() } returns ledgerAccount(bound = false)

            repository.startSigning()
            runCurrent()

            val failed = repository.signingState.value
            assertTrue(failed is LedgerSigningState.Failed)
            assertEquals(LedgerIssueKind.UNBOUND, failed.issue.kind)
            assertEquals(LedgerIssueRetry.NONE, failed.issue.retry)
            coVerify(exactly = 0) { ledgerSigningDataSource.connect(any()) }
        }

    /**
     * Simulates a session that is cancelled and immediately replaced while its own `sign` call is
     * still outstanding, by draining the replacement to completion from inside the outstanding
     * call's mock before it finally resolves — the "late" resolution can then only be published if
     * the generation guard is missing.
     */
    @Test
    fun aLateFailureFromACancelledSessionNeverOverwritesTheSessionThatReplacedIt() =
        runTest(dispatcher) {
            givenPczt(ledgerAccount())
            runCurrent()
            every { ledgerSigningDataSource.isLinked } returns true
            var signCalls = 0
            val signed = Pczt(byteArrayOf(5))
            val disconnected = mockk<LedgerException.Disconnected>(relaxed = true)
            coEvery { ledgerSigningDataSource.sign(any(), any(), any()) } coAnswers {
                signCalls++
                if (signCalls == 1) {
                    repository.cancelSigning()
                    repository.startSigning()
                    dispatcher.scheduler.runCurrent()
                    throw disconnected
                } else {
                    signed
                }
            }

            repository.startSigning()
            runCurrent()

            assertEquals(LedgerSigningState.Signed, repository.signingState.value)
            assertEquals(2, signCalls)
        }

    private suspend fun givenPczt(
        account: LedgerAccount,
        pczt: Pczt = Pczt(byteArrayOf(1)),
        proofsPczt: Pczt = Pczt(byteArrayOf(9)),
        proofsError: PcztException.AddProofsToPcztException? = null,
    ): Pczt {
        coEvery { accountDataSource.getSelectedAccount() } returns account
        coEvery { proposalDataSource.createProposal(any(), any()) } returns
            RegularTransactionProposal(
                destination = WalletAddress.Unified.new(RECIPIENT),
                amount = Zatoshi(1234L),
                memo = Memo(""),
                proposal = mockk<Proposal>()
            )
        coEvery { proposalDataSource.createPcztFromProposal(any(), any()) } returns pczt
        if (proofsError != null) {
            coEvery { proposalDataSource.addProofsToPczt(any()) } throws proofsError
        } else {
            coEvery { proposalDataSource.addProofsToPczt(any()) } returns proofsPczt
        }
        repository.createProposal(
            ZecSend(
                destination = WalletAddress.Unified.new(RECIPIENT),
                amount = Zatoshi(1234L),
                memo = Memo(""),
                proposal = null
            )
        )
        repository.createPCZTFromProposal()
        return pczt
    }

    private fun device(identifier: String) =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = identifier,
            rssi = -40,
        )

    private fun ledgerAccount(bound: Boolean = true) =
        LedgerAccount(
            sdkAccount = Account.new(AccountUuid.new(ByteArray(16) { it.toByte() })),
            unifiedAddress = "u1secret",
            transparentAddress = "t1secret",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
            deviceIdentity = if (bound) "tpk0-deadbeef" else null,
            zip32AccountIndex = if (bound) Zip32AccountIndex.new(0L) else null,
        )

    private companion object {
        const val RECIPIENT = "recipient"
    }
}
