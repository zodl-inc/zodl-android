package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.fixture.AccountFixture
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.migration.sim.FakeOrchardMigrationSdk
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.provider.MigrationNotifier
import co.electriccoin.zcash.ui.common.provider.PendingMigrationTorFailureStorageProvider
import co.electriccoin.zcash.ui.common.repository.MigrationTransferStateRepository
import co.electriccoin.zcash.ui.common.repository.PendingKeystoneMigrationPcztsRepository
import co.electriccoin.zcash.ui.common.repository.RestartMigrationScheduleRepository
import co.electriccoin.zcash.work.MigrationLiveDriver
import co.electriccoin.zcash.work.MigrationScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

class RestartMigrationUseCaseTest {
    /** Matches [co.electriccoin.zcash.ui.common.provider.MigrationTorPreferenceAccountScopingTest]'s
     *  own `account()` helper: a minimal [WalletAccount] stub with a stable, distinct accountUuid. */
    private fun account(uuid: UUID): WalletAccount =
        mockk(relaxed = true) {
            every { sdkAccount } returns AccountFixture.new(accountUuid = uuid)
        }

    @Test
    fun `invoke clears the run and clears all scheduled work for the selected account`() =
        runTest {
            val selected = account(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
            val accountKeyId = selected.sdkAccount.accountUuid.toStorageKeyId()

            // Seed the fake with an in-progress run so clearMigration has something to clear.
            val fakeSdk =
                FakeOrchardMigrationSdk().apply {
                    addTx(
                        FakeOrchardMigrationSdk.SimTx(
                            id = 1L,
                            isTransfer = true,
                            layer = 0,
                            scheduledHeight = 10L,
                            anchorBoundary = null,
                        )
                    )
                }

            val accountDataSource =
                mockk<AccountDataSource> {
                    coEvery { getSelectedAccount() } returns selected
                }
            val scheduler = mockk<MigrationScheduler>(relaxed = true)
            val torFailure = mockk<PendingMigrationTorFailureStorageProvider>(relaxed = true)
            val restartSchedule = mockk<RestartMigrationScheduleRepository>(relaxed = true)
            val keystonePczt = mockk<PendingKeystoneMigrationPcztsRepository>(relaxed = true)
            val notifier = mockk<MigrationNotifier>(relaxed = true)
            val liveDriver = mockk<MigrationLiveDriver>(relaxed = true)
            val transferStateRepository = mockk<MigrationTransferStateRepository>(relaxed = true)

            val useCase =
                RestartMigrationUseCase(
                    accountDataSource = accountDataSource,
                    getOrchardMigrationSdk =
                        mockk<GetOrchardMigrationSdkUseCase> {
                            coEvery { this@mockk() } returns fakeSdk
                        },
                    migrationScheduler = scheduler,
                    pendingMigrationTorFailureStorageProvider = torFailure,
                    restartMigrationScheduleRepository = restartSchedule,
                    pendingKeystoneMigrationPcztsRepository = keystonePczt,
                    migrationNotifier = notifier,
                    migrationLiveDriver = liveDriver,
                    migrationTransferStateRepository = transferStateRepository,
                )

            useCase()

            assertTrue(fakeSdk.clearMigrationCalled)
            verify { scheduler.cancel(accountKeyId) }
            coVerify { torFailure.store(accountKeyId, false) }
            verify { restartSchedule.consume(accountKeyId) }
            verify { keystonePczt.clear() }
            verify { notifier.cancel(accountKeyId) }
            verify { liveDriver.stop(accountKeyId) }
            verify { transferStateRepository.clear(accountKeyId) }
        }

    @Test
    fun `invoke stops the live driver before clearing the cache`() =
        runTest {
            // Pins the intended call order at the use-case level — NOT a proof that the
            // publish/clear race is closed (mocks model no coroutine suspension timing, so they
            // can't). stop() is cooperative cancellation: a loop already inside
            // publishFreshReadout's synchronous tail (past its last SDK read, before the
            // repository.publish() call) can still land that publish after both stop() and clear()
            // return regardless of this order. This ordering only reliably helps the common case —
            // the loop asleep in delay() — see MigrationLiveDriver.stop()'s own kdoc.
            val selected = account(UUID.fromString("00000000-0000-0000-0000-0000000000a2"))
            val accountKeyId = selected.sdkAccount.accountUuid.toStorageKeyId()
            val fakeSdk = FakeOrchardMigrationSdk()
            val accountDataSource =
                mockk<AccountDataSource> {
                    coEvery { getSelectedAccount() } returns selected
                }
            val liveDriver = mockk<MigrationLiveDriver>(relaxed = true)
            val transferStateRepository = mockk<MigrationTransferStateRepository>(relaxed = true)
            val callOrder = mutableListOf<String>()
            every { liveDriver.stop(accountKeyId) } answers { callOrder.add("stop") }
            every { transferStateRepository.clear(accountKeyId) } answers { callOrder.add("clear") }

            val useCase =
                RestartMigrationUseCase(
                    accountDataSource = accountDataSource,
                    getOrchardMigrationSdk =
                        mockk<GetOrchardMigrationSdkUseCase> {
                            coEvery { this@mockk() } returns fakeSdk
                        },
                    migrationScheduler = mockk(relaxed = true),
                    pendingMigrationTorFailureStorageProvider = mockk(relaxed = true),
                    restartMigrationScheduleRepository = mockk(relaxed = true),
                    pendingKeystoneMigrationPcztsRepository = mockk(relaxed = true),
                    migrationNotifier = mockk(relaxed = true),
                    migrationLiveDriver = liveDriver,
                    migrationTransferStateRepository = transferStateRepository,
                )

            useCase()

            kotlin.test.assertEquals(listOf("stop", "clear"), callOrder)
        }
}
