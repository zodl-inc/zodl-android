package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.WalletAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.datasource.SwapDataSource
import co.electriccoin.zcash.ui.common.datasource.SwapTransactionProposal
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import kotlin.test.Test

/**
 * [ProcessSwapTransactionUseCase] records a freshly-submitted swap: it writes the swap metadata
 * (deposit address + quote details, status PENDING) and then forwards each broadcast transaction id
 * to the swap provider as a deposit. Empty tx ids are skipped, and a per-deposit submission failure
 * is logged and swallowed so the remaining deposits still go out.
 *
 * Runs under Robolectric because the swallowed-failure path logs via `Twig` (android.util.Log).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProcessSwapTransactionUseCaseTest {
    private val origin = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc")
    private val destinationAsset = SwapAssetTestFixture.asset(tokenTicker = "eth", chainTicker = "eth")

    @Test
    fun savesSwapMetadataFromProposalAndQuote() =
        runTest {
            val fx = Fixtures()
            val proposal = swapProposal()

            fx.useCase(proposal, SubmitResult.Success(listOf("tx1")))

            verify(exactly = 1) {
                fx.metadataRepository.markTxAsSwap(
                    depositAddress = "deposit-address",
                    provider = "near",
                    origin = origin,
                    destination = destinationAsset,
                    totalFees = Zatoshi(1000),
                    totalFeesUsd = BigDecimal("0.5"),
                    amountOutFormatted = BigDecimal("1.23"),
                    mode = SwapMode.EXACT_INPUT,
                    status = SwapStatus.PENDING
                )
            }
        }

    @Test
    fun submitsEachTxIdAsADepositTransaction() =
        runTest {
            val fx = Fixtures()

            fx.useCase(swapProposal(), SubmitResult.Success(listOf("tx1", "tx2")))

            coVerify(exactly = 1) {
                fx.swapDataSource.submitDepositTransaction(txHash = "tx1", depositAddress = "deposit-address")
            }
            coVerify(exactly = 1) {
                fx.swapDataSource.submitDepositTransaction(txHash = "tx2", depositAddress = "deposit-address")
            }
            coVerify(exactly = 2) { fx.swapDataSource.submitDepositTransaction(any(), any()) }
        }

    @Test
    fun skipsEmptyTxIds() =
        runTest {
            val fx = Fixtures()

            fx.useCase(swapProposal(), SubmitResult.Success(listOf("tx1", "", "tx2")))

            coVerify(exactly = 2) { fx.swapDataSource.submitDepositTransaction(any(), any()) }
            coVerify(exactly = 0) { fx.swapDataSource.submitDepositTransaction(txHash = "", depositAddress = any()) }
        }

    @Test
    fun savesMetadataButSubmitsNothingWhenThereAreNoTxIds() =
        runTest {
            val fx = Fixtures()

            fx.useCase(swapProposal(), SubmitResult.Success(emptyList()))

            verify(exactly = 1) {
                fx.metadataRepository.markTxAsSwap(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
            coVerify(exactly = 0) { fx.swapDataSource.submitDepositTransaction(any(), any()) }
        }

    @Test
    fun continuesSubmittingRemainingDepositsWhenOneFails() =
        runTest {
            val fx = Fixtures()
            coEvery {
                fx.swapDataSource.submitDepositTransaction(txHash = "tx1", depositAddress = any())
            } throws RuntimeException("boom")

            // Must not throw — the failure is logged and the next deposit is still attempted.
            fx.useCase(swapProposal(), SubmitResult.Success(listOf("tx1", "tx2")))

            coVerify(exactly = 1) {
                fx.swapDataSource.submitDepositTransaction(txHash = "tx1", depositAddress = "deposit-address")
            }
            coVerify(exactly = 1) {
                fx.swapDataSource.submitDepositTransaction(txHash = "tx2", depositAddress = "deposit-address")
            }
        }

    private suspend fun swapProposal(): SwapTransactionProposal {
        val walletAddress = WalletAddress.Unified.new("deposit-address")
        val quote =
            mockk<SwapQuote> {
                every { provider } returns "near"
                every { originAsset } returns origin
                every { destinationAsset } returns this@ProcessSwapTransactionUseCaseTest.destinationAsset
                every { amountOutFormatted } returns BigDecimal("1.23")
                every { mode } returns SwapMode.EXACT_INPUT
            }
        return mockk {
            every { this@mockk.quote } returns quote
            every { destination } returns walletAddress
            every { totalFees } returns Zatoshi(1000)
            every { totalFeesUsd } returns BigDecimal("0.5")
        }
    }

    private class Fixtures {
        val metadataRepository = mockk<MetadataRepository>(relaxed = true)
        val swapDataSource = mockk<SwapDataSource>(relaxed = true)
        val useCase = ProcessSwapTransactionUseCaseImpl(metadataRepository, swapDataSource)
    }
}
