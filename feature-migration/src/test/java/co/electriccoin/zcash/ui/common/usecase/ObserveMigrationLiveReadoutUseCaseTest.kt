package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.MigrationState
import cash.z.ecc.android.sdk.MigrationTransferStates
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import co.electriccoin.zcash.ui.common.repository.MigrationLiveReadout
import co.electriccoin.zcash.ui.common.repository.MigrationTransferStateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMigrationLiveReadoutUseCaseTest {
    private fun readout(
        estimatedTip: Long = 100L,
        migrationState: MigrationState? = null,
    ): MigrationLiveReadout =
        MigrationLiveReadout(
            states = MigrationTransferStates(transfers = emptyList(), tipHeight = estimatedTip),
            estimatedTip = estimatedTip,
            estimatedSecondsPerBlock = 75L,
            migrationState = migrationState,
            hasOverdueTransfers = false,
        )

    @Test
    fun `a non-null cached value is emitted without ever calling the SDK fallback`() =
        runTest {
            val cached = readout(estimatedTip = 111L)
            val repository =
                mockk<MigrationTransferStateRepository> {
                    every { observe(any()) } returns MutableStateFlow(cached)
                }
            // Deliberately NOT stubbed (strict mock) — any call to it fails the test loudly
            // instead of silently degrading, making "the fallback was never invoked" airtight.
            val getOrchardMigrationSdk = mockk<GetOrchardMigrationSdkUseCase>()

            val useCase = ObserveMigrationLiveReadoutUseCase(repository, getOrchardMigrationSdk)

            val result = useCase("account-1").first()

            assertEquals(cached, result)
            coVerify(exactly = 0) { getOrchardMigrationSdk() }
        }

    @Test
    fun `a null cached value falls back to one direct SDK read`() =
        runTest {
            // getMigrationStateUnreconciled() itself is non-null (only MigrationLiveReadout's own
            // field is nullable), so the fixture needs a real MigrationState value here.
            val fallback = readout(estimatedTip = 222L, migrationState = MigrationState.NotStarted)
            val repository =
                mockk<MigrationTransferStateRepository> {
                    every { observe(any()) } returns MutableStateFlow(null)
                }
            val sdk =
                mockk<OrchardMigrationSdk> {
                    coEvery { getMigrationTransferStates() } returns fallback.states
                    coEvery { estimatedChainTip() } returns fallback.estimatedTip
                    coEvery { estimatedSecondsPerBlock() } returns fallback.estimatedSecondsPerBlock
                    coEvery { getMigrationStateUnreconciled() } returns requireNotNull(fallback.migrationState)
                    every { nu7ActivationHeight } returns fallback.nu7ActivationHeight
                }
            val getOrchardMigrationSdk =
                mockk<GetOrchardMigrationSdkUseCase> {
                    coEvery { this@mockk() } returns sdk
                }

            val useCase = ObserveMigrationLiveReadoutUseCase(repository, getOrchardMigrationSdk)

            val result = useCase("account-1").first()

            assertEquals(fallback, result)
        }

    @Test
    fun `an SDK fallback failure emits null instead of propagating`() =
        runTest {
            val repository =
                mockk<MigrationTransferStateRepository> {
                    every { observe(any()) } returns MutableStateFlow(null)
                }
            val sdk =
                mockk<OrchardMigrationSdk> {
                    coEvery { getMigrationTransferStates() } throws IllegalStateException("database is locked")
                }
            val getOrchardMigrationSdk =
                mockk<GetOrchardMigrationSdkUseCase> {
                    coEvery { this@mockk() } returns sdk
                }

            val useCase = ObserveMigrationLiveReadoutUseCase(repository, getOrchardMigrationSdk)

            val result = useCase("account-1").first()

            assertNull(result, "a fallback SDK read that throws must yield null, mirroring runCatching { }.getOrNull()")
        }

    @Test
    fun `the internal ticker re-triggers the SDK fallback on every beat while the cache stays null`() =
        // ObserveMigrationLiveReadoutUseCase's own .flowOn(Dispatchers.Default) is deliberate
        // (keeps the ticker's wait off whichever dispatcher collects the flow — see its kdoc), but
        // it also means the ticker's delay() genuinely runs on a REAL dispatcher, outside runTest's
        // virtual-time scheduler — advanceTimeBy/runCurrent cannot fast-forward it. This test
        // therefore observes two REAL SDK calls (synchronized via a Channel, not a fixed sleep, to
        // avoid flakiness), bounded by a generous real timeout instead of asserting the exact
        // production interval value (which is also private).
        runTest(timeout = 30.seconds) {
            val calls = Channel<Unit>(Channel.UNLIMITED)
            val repository =
                mockk<MigrationTransferStateRepository> {
                    every { observe(any()) } returns MutableStateFlow(null)
                }
            val sdk =
                mockk<OrchardMigrationSdk> {
                    coEvery { getMigrationTransferStates() } coAnswers {
                        calls.trySend(Unit)
                        null
                    }
                    coEvery { estimatedChainTip() } returns -1L
                    coEvery { estimatedSecondsPerBlock() } returns 0L
                    coEvery { getMigrationStateUnreconciled() } returns MigrationState.NotStarted
                }
            val getOrchardMigrationSdk =
                mockk<GetOrchardMigrationSdkUseCase> {
                    coEvery { this@mockk() } returns sdk
                }

            val useCase = ObserveMigrationLiveReadoutUseCase(repository, getOrchardMigrationSdk)

            val job =
                backgroundScope.launch {
                    useCase("account-1").collect { }
                }

            withContext(Dispatchers.Default) {
                withTimeout(5.seconds) { calls.receive() }
                withTimeout(20.seconds) { calls.receive() }
            }

            job.cancel()
        }
}
