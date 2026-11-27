package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import co.electriccoin.zcash.ui.common.provider.KeepScreenOnSyncSessionProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * MOB-2041: the keep-screen-on session ends when the sync it was started for finishes, and not on a
 * synced status reported before that sync has begun.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveKeepScreenOnSyncSessionUseCaseTest {
    private val syncSession = MutableStateFlow<Boolean?>(null)
    private val statusFlow = MutableStateFlow(Synchronizer.Status.INITIALIZING)
    private val synchronizerFlow =
        MutableStateFlow<Synchronizer?>(
            mockk<Synchronizer> { every { status } returns statusFlow }
        )

    private val sessionProvider =
        mockk<KeepScreenOnSyncSessionProvider> {
            every { observe() } returns syncSession
            coEvery { clear() } answers { syncSession.value = null }
        }

    private val synchronizerProvider =
        mockk<SynchronizerProvider> { every { synchronizer } returns synchronizerFlow }

    private val useCase =
        ObserveKeepScreenOnSyncSessionUseCase(
            synchronizerProvider = synchronizerProvider,
            keepScreenOnSyncSessionProvider = sessionProvider,
        )

    @Test
    fun sessionEndsWhenTheSyncFinishes() =
        runTest {
            syncSession.value = true
            backgroundScope.launch { useCase() }
            runCurrent()

            statusFlow.value = Synchronizer.Status.SYNCING
            runCurrent()
            assertEquals(true, syncSession.value)

            statusFlow.value = Synchronizer.Status.SYNCED
            runCurrent()
            assertNull(syncSession.value)
        }

    @Test
    fun syncedBeforeSyncingDoesNotEndTheSession() =
        runTest {
            syncSession.value = true
            backgroundScope.launch { useCase() }
            runCurrent()

            statusFlow.value = Synchronizer.Status.SYNCED
            runCurrent()

            assertEquals(true, syncSession.value)
        }

    @Test
    fun sessionSurvivesASynchronizerReset() =
        runTest {
            syncSession.value = true
            backgroundScope.launch { useCase() }
            runCurrent()

            statusFlow.value = Synchronizer.Status.SYNCING
            runCurrent()

            val restarted = MutableStateFlow(Synchronizer.Status.SYNCED)
            synchronizerFlow.value = null
            runCurrent()
            synchronizerFlow.value = mockk<Synchronizer> { every { status } returns restarted }
            runCurrent()

            assertEquals(true, syncSession.value)

            restarted.value = Synchronizer.Status.SYNCING
            runCurrent()
            restarted.value = Synchronizer.Status.SYNCED
            runCurrent()

            assertNull(syncSession.value)
        }

    @Test
    fun syncingSeenBeforeTheSessionStartedDoesNotCount() =
        runTest {
            backgroundScope.launch { useCase() }
            runCurrent()

            statusFlow.value = Synchronizer.Status.SYNCING
            runCurrent()
            statusFlow.value = Synchronizer.Status.SYNCED
            runCurrent()

            syncSession.value = true
            runCurrent()

            assertEquals(true, syncSession.value)
        }
}
