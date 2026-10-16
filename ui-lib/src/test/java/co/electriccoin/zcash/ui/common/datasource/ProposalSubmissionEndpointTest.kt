package co.electriccoin.zcash.ui.common.datasource

import cash.z.ecc.android.sdk.model.PersistableWallet
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Transaction submission endpoint selection behavior.
 *
 * Prior to the fix, automatic mode broadcast transactions to ALL bundled endpoints (OHTTP gateway
 * + 6 clearnet servers), defeating OHTTP privacy by exposing the raw transaction to every operator.
 *
 * After the fix, [ProposalDataSourceImpl.getSubmissionEndpoints] always returns just the wallet's
 * current endpoint, regardless of automatic/manual mode -- the mode logic itself is gone, so these
 * tests exercise the real (internal) function directly rather than a reimplementation, otherwise a
 * regression reintroducing the all-endpoints broadcast would pass this suite unnoticed.
 */
class ProposalSubmissionEndpointTest {
    private val ohttpGatewayMainnet = LightWalletEndpoint(host = "ohttp-gateway.example", port = 443, isSecure = true)
    private val clearnetServer1 = LightWalletEndpoint(host = "zec.rocks", port = 443, isSecure = true)

    private val persistableWalletProvider = mockk<PersistableWalletProvider>()
    private val dataSource =
        ProposalDataSourceImpl(
            synchronizerProvider = mockk<SynchronizerProvider>(),
            persistableWalletProvider = persistableWalletProvider,
        )

    @Test
    fun submitsOnlyToTheCurrentOhttpGatewayEndpoint() =
        runTest {
            coEvery { persistableWalletProvider.requirePersistableWallet() } returns wallet(ohttpGatewayMainnet)

            val result = dataSource.getSubmissionEndpoints()

            assertEquals(
                "Submission must go only to the current endpoint, never every bundled server",
                listOf(ohttpGatewayMainnet),
                result
            )
        }

    @Test
    fun submitsOnlyToAManuallySelectedClearnetEndpoint() =
        runTest {
            coEvery { persistableWalletProvider.requirePersistableWallet() } returns wallet(clearnetServer1)

            val result = dataSource.getSubmissionEndpoints()

            assertEquals(
                "A manually-selected fallback must be honored as the sole submission target",
                listOf(clearnetServer1),
                result
            )
        }

    private fun wallet(walletEndpoint: LightWalletEndpoint) =
        mockk<PersistableWallet> { every { endpoint } returns walletEndpoint }
}
