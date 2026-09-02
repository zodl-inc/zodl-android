package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.WalletCoordinator
import cash.z.ecc.android.sdk.model.PersistableWallet
import cash.z.ecc.android.sdk.model.SeedPhrase
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days

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

    /**
     * Simulates the collector re-invoking this use case on every `isSeedMismatch` flip — far more
     * than the retry cap. Only [RecoverFromSeedMismatchUseCase]'s `MAX_RECOVERY_ATTEMPTS` (3) real
     * erase attempts are made; once exhausted, further invocations fail fast without hammering the
     * SDK again — this is the unthrottled wipe-retry loop fix.
     */
    @Test
    fun repeatedFailuresAreCappedAndStopHittingTheSdk() =
        runTest {
            every { walletCoordinator.deleteSdkDataFlow() } returns flowOf(false)

            repeat(5) {
                assertFailsWith<SeedMismatchRecoveryException> { useCase() }
            }

            coVerify(exactly = 3) { walletCoordinator.deleteSdkDataFlow() }
            coVerify(exactly = 0) { persistableWalletProvider.store(any()) }
        }

    /**
     * A success resets the failure counter, and a subsequent failure starts a fresh cap rather
     * than inheriting the earlier count.
     */
    @Test
    fun successAfterPriorFailuresResetsTheCounter() =
        runTest {
            every { walletCoordinator.deleteSdkDataFlow() } returns flowOf(false)
            assertFailsWith<SeedMismatchRecoveryException> { useCase() }
            assertFailsWith<SeedMismatchRecoveryException> { useCase() }

            every { walletCoordinator.deleteSdkDataFlow() } returns flowOf(true)
            useCase()

            coVerify(exactly = 1) { persistableWalletProvider.store(any()) }

            every { walletCoordinator.deleteSdkDataFlow() } returns flowOf(false)
            assertFailsWith<SeedMismatchRecoveryException> { useCase() }
            assertFailsWith<SeedMismatchRecoveryException> { useCase() }
            coVerify(exactly = 1) { persistableWalletProvider.store(any()) }
        }

    /**
     * A wallet concurrently nulled out (Reset Zashi racing this recovery) leaves
     * [WalletCoordinator.deleteSdkDataFlow]'s inner wallet read null, so the flow never emits or
     * closes. A bounded timeout must count as a failed attempt exactly like an explicit `false`
     * result, rather than hanging this use case (and the collector invoking it) forever.
     */
    @Test
    fun eraseTimeoutCountsAsFailedAttemptAndThrows() =
        runTest {
            every { walletCoordinator.deleteSdkDataFlow() } returns flow<Boolean> { delay(1.days) }

            assertFailsWith<SeedMismatchRecoveryException> { useCase() }

            coVerify(exactly = 0) { persistableWalletProvider.store(any()) }
        }

    /**
     * If Reset Zashi clears the persisted wallet while the erase is in flight, `store()` must not
     * resurrect the deleted wallet's seed.
     */
    @Test
    fun storeIsSkippedWhenWalletWasClearedDuringErase() =
        runTest {
            every { wallet.network } returns ZcashNetwork.Mainnet
            every { wallet.seedPhrase } returns seedPhrase("abandon")
            coEvery { persistableWalletProvider.getPersistableWallet() } returnsMany listOf(wallet, null)
            every { walletCoordinator.deleteSdkDataFlow() } returns flowOf(true)

            useCase()

            coVerify(exactly = 0) { persistableWalletProvider.store(any()) }
        }

    /**
     * If the user restores a different wallet while the erase is in flight, `store()` must not
     * overwrite it with the stale, now-wiped wallet's recovery copy.
     */
    @Test
    fun storeIsSkippedWhenADifferentWalletWasStoredDuringErase() =
        runTest {
            every { wallet.network } returns ZcashNetwork.Mainnet
            every { wallet.seedPhrase } returns seedPhrase("abandon")
            val restoredWallet =
                mockk<PersistableWallet>(relaxed = true) {
                    every { network } returns ZcashNetwork.Mainnet
                    every { seedPhrase } returns seedPhrase("zoo")
                }
            coEvery { persistableWalletProvider.getPersistableWallet() } returnsMany listOf(wallet, restoredWallet)
            every { walletCoordinator.deleteSdkDataFlow() } returns flowOf(true)

            useCase()

            coVerify(exactly = 0) { persistableWalletProvider.store(any()) }
        }

    private fun seedPhrase(word: String) = SeedPhrase(List(SeedPhrase.SEED_PHRASE_SIZE) { word })
}
