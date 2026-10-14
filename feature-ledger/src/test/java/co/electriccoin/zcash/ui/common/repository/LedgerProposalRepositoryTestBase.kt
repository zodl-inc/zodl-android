package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.exception.PcztException
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher

/**
 * The mocks, the repository under test and the proposal fixtures the Ledger signing session tests
 * share: [LedgerProposalRepositoryTest] covers the session itself, retry, cancellation and
 * submission, and [LedgerProposalRepositoryScanTest] the scan, the device picker and the connect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class LedgerProposalRepositoryTestBase {
    protected val dispatcher = StandardTestDispatcher()

    protected val accountDataSource = mockk<AccountDataSource>()
    protected val proposalDataSource = mockk<ProposalDataSource>()
    protected val ledgerDeviceDataSource = mockk<LedgerDeviceDataSource>()
    protected val ledgerSigningDataSource = mockk<LedgerSigningDataSource>(relaxed = true)

    protected val devices = MutableStateFlow<List<LedgerBluetoothDevice>>(emptyList())

    protected val repository =
        LedgerProposalRepositoryImpl(
            accountDataSource = accountDataSource,
            proposalDataSource = proposalDataSource,
            ledgerDeviceDataSource = ledgerDeviceDataSource,
            ledgerSigningDataSource = ledgerSigningDataSource,
        ).apply { scope = CoroutineScope(dispatcher) }

    init {
        every { ledgerDeviceDataSource.observeDevices() } returns devices
        every { ledgerDeviceDataSource.isLocationOffForScan() } returns false
    }

    protected suspend fun givenPczt(
        account: LedgerAccount,
        pczt: Pczt = Pczt(byteArrayOf(1)),
        proofsPczt: Pczt = Pczt(byteArrayOf(9)),
        proofsError: PcztException.AddProofsToPcztException? = null,
        proofsGate: CompletableDeferred<Unit>? = null,
        onProofsCancelled: () -> Unit = {},
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
        coEvery { proposalDataSource.addProofsToPczt(any()) } coAnswers {
            try {
                proofsGate?.await()
            } catch (e: CancellationException) {
                onProofsCancelled()
                throw e
            }
            if (proofsError != null) throw proofsError
            proofsPczt
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

    protected fun device(identifier: String) =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = identifier,
            rssi = -40,
        )

    protected fun ledgerAccount(bound: Boolean = true) =
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
