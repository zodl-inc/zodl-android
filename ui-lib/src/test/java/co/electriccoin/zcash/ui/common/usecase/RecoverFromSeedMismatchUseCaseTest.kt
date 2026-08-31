package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.WalletCoordinator
import cash.z.ecc.android.sdk.model.PersistableWallet
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * MOB-1397 review coverage: [RecoverFromSeedMismatchUseCase] wipes the SDK database via the
 * lockout-aware [WalletCoordinator.deleteSdkDataFlow] and only re-persists (flips
 * [cash.z.ecc.android.sdk.WalletInitMode] to `RestoreWallet`) once that erase actually succeeds.
 * A failed erase must neither silently proceed to `store()` nor be retried without bound.
 */
class RecoverFromSeedMismatchUseCaseTest {
    private val wallet = mockk<PersistableWallet>(relaxed = true)
    private val persistableWalletProvider =
        mockk<PersistableWalletProvider>(relaxed = true) {
            coEvery { getPersistableWallet() } returns wallet
        }
    private val walletCoordinator = mockk<WalletCoordinator>()

    private val useCase = RecoverFromSeedMismatchUseCase(persistableWalletProvider, walletCoordinator)

    @Test
    fun successfulEraseWipesThenRePersists() =
        runTest {
            every { walletCoordinator.deleteSdkDataFlow() } returns flowOf(true)

            useCase()

            coVerify(exactly = 1) { walletCoordinator.deleteSdkDataFlow() }
            coVerify(exactly = 1) { persistableWalletProvider.store(any()) }
        }

    @Test
    fun noWalletStoredIsANoOp() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns null

            useCase()

            coVerify(exactly = 0) { walletCoordinator.deleteSdkDataFlow() }
            coVerify(exactly = 0) { persistableWalletProvider.store(any()) }
        }

    @Test
    fun failedEraseDoesNotPersistAndThrowsExplicitError() =
        runTest {
            every { walletCoordinator.deleteSdkDataFlow() } returns flowOf(false)

            assertFailsWith<SeedMismatchRecoveryException> { useCase() }

            coVerify(exactly = 0) { persistableWalletProvider.store(any()) }
        }

    @Test
    fun repeatedFailuresAreCappedAndStopHittingTheSdk() =
        runTest {
            every { walletCoordinator.deleteSdkDataFlow() } returns flowOf(false)

            // Simulate WalletViewModel's collector re-invoking this on every isSeedMismatch flip —
            // far more than the retry cap.
            repeat(5) {
                assertFailsWith<SeedMismatchRecoveryException> { useCase() }
            }

            // Only MAX_RECOVERY_ATTEMPTS (3) real erase attempts are made; once exhausted, further
            // invocations fail fast without hammering the SDK again — this is the unthrottled
            // wipe-retry loop fix.
            coVerify(exactly = 3) { walletCoordinator.deleteSdkDataFlow() }
            coVerify(exactly = 0) { persistableWalletProvider.store(any()) }
        }

    @Test
    fun successAfterPriorFailuresResetsTheCounter() =
        runTest {
            every { walletCoordinator.deleteSdkDataFlow() } returns flowOf(false)
            assertFailsWith<SeedMismatchRecoveryException> { useCase() }
            assertFailsWith<SeedMismatchRecoveryException> { useCase() }

            every { walletCoordinator.deleteSdkDataFlow() } returns flowOf(true)
            useCase()

            coVerify(exactly = 1) { persistableWalletProvider.store(any()) }

            // A subsequent failure starts a fresh cap rather than inheriting the earlier count.
            every { walletCoordinator.deleteSdkDataFlow() } returns flowOf(false)
            assertFailsWith<SeedMismatchRecoveryException> { useCase() }
            assertFailsWith<SeedMismatchRecoveryException> { useCase() }
            coVerify(exactly = 1) { persistableWalletProvider.store(any()) }
        }
}
