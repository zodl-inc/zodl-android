package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.exception.PcztException
import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.TransactionSizeBound
import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZecSend
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.MigrationSweepTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ProposalDataSource
import co.electriccoin.zcash.ui.common.datasource.RegularTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.TexUnsupportedOnKSException
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.provider.SignerCapabilities
import co.electriccoin.zcash.ui.common.provider.SignerCapabilitiesProvider
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeystoneProposalRepositoryTest {
    private val accountDataSource = mockk<AccountDataSource>()
    private val proposalDataSource = mockk<ProposalDataSource>()
    private val signerCapabilities =
        mockk<SignerCapabilitiesProvider> {
            coEvery { forAccount(any()) } returns SignerCapabilities(TransactionSizeBound(maxShieldedActions = 42))
            every { endFlow() } just Runs
        }

    private val keystoneSDKProvider = mockk<KeystoneSDKProvider>()

    private val repository: KeystoneProposalRepository =
        KeystoneProposalRepositoryImpl(
            accountDataSource = accountDataSource,
            proposalDataSource = proposalDataSource,
            keystoneSDKProvider = keystoneSDKProvider,
            signerCapabilitiesProvider = signerCapabilities,
        )

    @Test
    fun setMigrationSweepProposalPublishesAMigrationSweepTransactionProposal() {
        val proposal = mockk<Proposal>()
        val amount = Zatoshi(1234L)

        repository.setMigrationSweepProposal(proposal, amount)

        val stored = repository.transactionProposal.value
        assertEquals(MigrationSweepTransactionProposal(amount, proposal), stored)
    }

    /**
     * A multi-step PCZT rejection means "TEX is unsupported on Keystone" only when the proposal
     * actually pays a TEX address — the one flow where a multi-step proposal is the expected shape.
     */
    @Test
    fun aMultiStepPcztRejectionOfATexPaymentBecomesTexUnsupportedOnKeystone() =
        runBlocking {
            givenCurrentProposalPays(WalletAddress.Tex.new(RECIPIENT))
            coEvery { proposalDataSource.createPcztFromProposal(any(), any(), any()) } throws
                PcztException.MultiStepProposalUnsupportedException()

            assertFailsWith<TexUnsupportedOnKSException> {
                repository.createPCZTFromProposal()
            }

            Unit
        }

    @Test
    fun aMultiStepPcztRejectionOfANonTexPaymentPropagatesUntouched() =
        runBlocking {
            givenCurrentProposalPays(WalletAddress.Unified.new(RECIPIENT))
            coEvery { proposalDataSource.createPcztFromProposal(any(), any(), any()) } throws
                PcztException.MultiStepProposalUnsupportedException()

            assertFailsWith<PcztException.MultiStepProposalUnsupportedException> {
                repository.createPCZTFromProposal()
            }

            Unit
        }

    /**
     * Migration sweeps have no payment destination at all, so their multi-step rejection must never
     * be dressed up as a TEX error.
     */
    @Test
    fun aMultiStepPcztRejectionOfAMigrationSweepPropagatesUntouched() =
        runBlocking {
            repository.setMigrationSweepProposal(mockk<Proposal>(), Zatoshi(1234L))
            coEvery { accountDataSource.getSelectedAccount() } returns mockk(relaxed = true)
            coEvery { proposalDataSource.createPcztFromProposal(any(), any(), any()) } throws
                PcztException.MultiStepProposalUnsupportedException()

            assertFailsWith<PcztException.MultiStepProposalUnsupportedException> {
                repository.createPCZTFromProposal()
            }

            Unit
        }

    @Test
    fun aSuccessfulSubmitEndsTheSignerCapabilitiesFlow() =
        runBlocking {
            givenSignedPczt()
            coEvery { proposalDataSource.submitTransaction(any<Pczt>(), any<Pczt>()) } returns
                SubmitResult.Success(listOf("tx"))

            repository.submit()

            verify(exactly = 1) { signerCapabilities.endFlow() }
        }

    @Test
    fun aFailedSubmitKeepsTheSignerCapabilitiesFlow() =
        runBlocking {
            givenSignedPczt()
            coEvery { proposalDataSource.submitTransaction(any<Pczt>(), any<Pczt>()) } returns
                SubmitResult.Partial(listOf("tx"), listOf("status"))

            repository.submit()

            verify(exactly = 0) { signerCapabilities.endFlow() }
        }

    private suspend fun givenSignedPczt() {
        givenCurrentProposalPays(WalletAddress.Unified.new(RECIPIENT))
        val pczt = mockk<Pczt>(relaxed = true)
        coEvery { proposalDataSource.createPcztFromProposal(any(), any(), any()) } returns pczt
        coEvery { proposalDataSource.addProofsToPczt(any()) } returns mockk<Pczt>(relaxed = true)
        repository.createPCZTFromProposal()
        val stamp = "keystone:fw_version".toByteArray(Charsets.US_ASCII)
        every { keystoneSDKProvider.parsePczt(any()) } returns
            byteArrayOf(0x01) + stamp + byteArrayOf(0x03, 13, 0, 1) + byteArrayOf(0x09)
        repository.parsePCZT(mockk(relaxed = true))
    }

    private suspend fun givenCurrentProposalPays(destination: WalletAddress) {
        coEvery { accountDataSource.getSelectedAccount() } returns mockk(relaxed = true)
        coEvery { proposalDataSource.createProposal(any(), any()) } returns
            RegularTransactionProposal(
                destination = destination,
                amount = Zatoshi(1234L),
                memo = Memo(""),
                proposal = mockk<Proposal>()
            )
        repository.createProposal(
            ZecSend(
                destination = destination,
                amount = Zatoshi(1234L),
                memo = Memo(""),
                proposal = null
            )
        )
    }

    private companion object {
        const val RECIPIENT = "recipient"
    }
}
