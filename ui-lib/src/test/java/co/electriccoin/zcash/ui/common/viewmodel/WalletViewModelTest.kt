package co.electriccoin.zcash.ui.common.viewmodel

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.migration.MIGRATION_DUST_THRESHOLD_ZATOSHI
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import co.electriccoin.zcash.ui.common.usecase.RecoverFromSeedMismatchUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val accountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })

    private fun zeroBalance() = WalletBalance(Zatoshi(0), Zatoshi(0), Zatoshi(0), Zatoshi(0))

    private fun balancesWithOrchard(orchardZatoshi: Long): Map<AccountUuid, AccountBalance> =
        mapOf(
            accountUuid to
                AccountBalance(
                    sapling = zeroBalance(),
                    orchard = zeroBalance().copy(available = Zatoshi(orchardZatoshi)),
                    ironwood = zeroBalance(),
                    unshielded = Zatoshi(0),
                )
        )

    // MOB-1620: gated on MIGRATION_DUST_THRESHOLD_ZATOSHI rather than a bare `> 0L`, so a
    // dust-only residual (e.g. left over after a re-import resets the locally-tracked
    // isIronwoodAnnouncementShown flag) never re-triggers the full-screen announcement.
    private fun vm(
        orchardZatoshi: Long = 0L,
        isSeedMismatch: MutableStateFlow<Boolean> = MutableStateFlow(false),
        recoverFromSeedMismatch: RecoverFromSeedMismatchUseCase =
            mockk {
                coEvery { this@mockk.invoke() } returns Unit
            },
    ): WalletViewModel {
        val synchronizer =
            mockk<Synchronizer> {
                every { network } returns ZcashNetwork.Mainnet
                every { fullyScannedHeight } returns MutableStateFlow(BlockHeight.new(3_500_000L))
            }
        val synchronizerProvider =
            mockk<SynchronizerProvider> {
                every { this@mockk.synchronizer } returns MutableStateFlow(synchronizer)
                every { walletBalances } returns flowOf(balancesWithOrchard(orchardZatoshi))
                every { this@mockk.isSeedMismatch } returns isSeedMismatch
            }
        val walletRepository =
            mockk<WalletRepository>(relaxed = true) {
                every { isIronwoodAnnouncementShown } returns MutableStateFlow(null)
            }
        return WalletViewModel(synchronizerProvider, walletRepository, recoverFromSeedMismatch)
    }

    @Test
    fun dustOnlyOrchardBalanceDoesNotShowAnnouncement() =
        runTest {
            val vm = vm(orchardZatoshi = MIGRATION_DUST_THRESHOLD_ZATOSHI)
            val collectJob = launch { vm.shouldShowIronwoodAnnouncement.collect {} }
            advanceUntilIdle()

            assertEquals(false, vm.shouldShowIronwoodAnnouncement.value)
            collectJob.cancel()
        }

    @Test
    fun aboveDustOrchardBalanceShowsAnnouncement() =
        runTest {
            val vm = vm(orchardZatoshi = MIGRATION_DUST_THRESHOLD_ZATOSHI + 1L)
            val collectJob = launch { vm.shouldShowIronwoodAnnouncement.collect {} }
            advanceUntilIdle()

            assertEquals(true, vm.shouldShowIronwoodAnnouncement.value)
            collectJob.cancel()
        }

    // MOB-1397: WalletViewModel.init auto-triggers RecoverFromSeedMismatchUseCase the moment
    // SynchronizerProvider.isSeedMismatch flips to true, with no user interaction required.
    @Test
    fun seedMismatchTriggersAutoRecoveryExactlyOnce() =
        runTest {
            val isSeedMismatch = MutableStateFlow(false)
            val recoverFromSeedMismatch =
                mockk<RecoverFromSeedMismatchUseCase> {
                    coEvery { this@mockk.invoke() } returns Unit
                }
            vm(isSeedMismatch = isSeedMismatch, recoverFromSeedMismatch = recoverFromSeedMismatch)
            advanceUntilIdle()
            coVerify(exactly = 0) { recoverFromSeedMismatch.invoke() }

            isSeedMismatch.value = true
            advanceUntilIdle()

            coVerify(exactly = 1) { recoverFromSeedMismatch.invoke() }
        }

    // A recovery failure (e.g. SeedMismatchRecoveryException once retries are exhausted) must be
    // caught rather than crashing the ViewModel's collector — it's only logged.
    @Test
    fun seedMismatchRecoveryFailureIsCaughtAndDoesNotCrashCollector() =
        runTest {
            val isSeedMismatch = MutableStateFlow(false)
            val recoverFromSeedMismatch =
                mockk<RecoverFromSeedMismatchUseCase> {
                    coEvery { this@mockk.invoke() } throws RuntimeException("erase failed")
                }
            vm(isSeedMismatch = isSeedMismatch, recoverFromSeedMismatch = recoverFromSeedMismatch)
            advanceUntilIdle()

            isSeedMismatch.value = true
            advanceUntilIdle()

            coVerify(exactly = 1) { recoverFromSeedMismatch.invoke() }
            // The collector is still alive and would react to a further flip. The intermediate
            // advanceUntilIdle() lets the collector actually observe `false` before flipping back
            // to `true` — StateFlow only guarantees the latest value, so two writes with no
            // dispatch in between could otherwise collapse into a single (skipped) emission.
            isSeedMismatch.value = false
            advanceUntilIdle()
            isSeedMismatch.value = true
            advanceUntilIdle()
            coVerify(exactly = 2) { recoverFromSeedMismatch.invoke() }
        }
}
