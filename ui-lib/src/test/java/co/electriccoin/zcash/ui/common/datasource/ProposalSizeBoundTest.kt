package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.TransactionSizeBound
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.SignerCapabilities
import co.electriccoin.zcash.ui.common.provider.SignerCapabilitiesProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProposalSizeBoundTest {
    private val account = mockk<WalletAccount>()
    private val bound = TransactionSizeBound(maxShieldedActions = 42)
    private val provider =
        mockk<SignerCapabilitiesProvider> {
            coEvery { forAccount(account) } returns SignerCapabilities(bound)
        }

    @Test
    fun sendsAreBoundedByTheSignersCapabilities() =
        runTest {
            val sends =
                listOf<TransactionProposal>(
                    mockk<RegularTransactionProposal>(),
                    mockk<Zip321TransactionProposal>(),
                    mockk<ExactInputSwapTransactionProposal>(),
                    mockk<ExactOutputSwapTransactionProposal>()
                )

            sends.forEach { assertEquals(bound, provider.sizeBoundFor(account, it)) }
        }

    @Test
    fun shieldingIsNeverBounded() =
        runTest {
            assertEquals(
                TransactionSizeBound.CONSENSUS,
                provider.sizeBoundFor(account, mockk<ShieldTransactionProposal>())
            )
            coVerify(exactly = 0) { provider.forAccount(any()) }
        }

    @Test
    fun theMigrationSweepIsNeverBounded() =
        runTest {
            assertEquals(
                TransactionSizeBound.CONSENSUS,
                provider.sizeBoundFor(account, mockk<MigrationSweepTransactionProposal>())
            )
            coVerify(exactly = 0) { provider.forAccount(any()) }
        }
}
