package co.electriccoin.zcash.ui.common.repository

import cash.z.ecc.android.sdk.Synchronizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * [isDisconnectedFor]: the grace both the automatic server selection and the "Disconnected" home message
 * apply to [Synchronizer.Status.DISCONNECTED] (2026-10-22: a status blip around a lifecycle change flashed
 * the banner and probed the gateway at once).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DisconnectedGraceTest {
    @Test
    fun `a disconnected status shorter than the grace never counts`() =
        runTest {
            val status = MutableStateFlow(Synchronizer.Status.SYNCED)
            val seen = mutableListOf<Boolean>()
            val job = status.isDisconnectedFor().onEach { seen += it }.launchIn(this)
            runCurrent()

            status.value = Synchronizer.Status.DISCONNECTED
            advanceTimeBy(DISCONNECTED_GRACE - 1.seconds)
            status.value = Synchronizer.Status.SYNCING
            advanceTimeBy(DISCONNECTED_GRACE * 2)

            assertEquals(listOf(false), seen)
            job.cancel()
        }

    @Test
    fun `a disconnected status that lasts the grace counts once and clears at once on recovery`() =
        runTest {
            val status = MutableStateFlow(Synchronizer.Status.SYNCED)
            val seen = mutableListOf<Boolean>()
            val job = status.isDisconnectedFor().onEach { seen += it }.launchIn(this)
            runCurrent()

            status.value = Synchronizer.Status.DISCONNECTED
            advanceTimeBy(DISCONNECTED_GRACE)
            runCurrent()
            assertEquals(listOf(false, true), seen)

            advanceTimeBy(DISCONNECTED_GRACE)
            assertEquals(listOf(false, true), seen, "a sustained outage is one edge")

            status.value = Synchronizer.Status.SYNCED
            runCurrent()
            assertEquals(listOf(false, true, false), seen)
            job.cancel()
        }

    @Test
    fun `a status that starts disconnected waits the full grace too`() =
        runTest {
            val status = MutableStateFlow(Synchronizer.Status.DISCONNECTED)
            val seen = mutableListOf<Boolean>()
            val job = status.isDisconnectedFor().onEach { seen += it }.launchIn(this)
            runCurrent()
            assertEquals(listOf(false), seen, "emits at once so a combine never waits on it")

            advanceTimeBy(DISCONNECTED_GRACE)
            runCurrent()
            assertEquals(listOf(false, true), seen)
            job.cancel()
        }

    @Test
    fun `the grace is twenty seconds`() = assertEquals(20.seconds, DISCONNECTED_GRACE)
}
