package co.electriccoin.zcash.ui.common.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.repeatOnLifecycle
import co.electriccoin.zcash.ui.common.usecase.HandleSharedPaymentUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPaymentViewModelTest {
    private val handleSharedPayment = mockk<HandleSharedPaymentUseCase>()
    private val vm = SharedPaymentViewModel(handleSharedPayment)
    private val image = Uri.parse("content://test/payment.png")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        coEvery { handleSharedPayment(any<String>()) } returns Unit
        coEvery { handleSharedPayment(any<Uri>()) } returns Unit
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun shareIsHandledOnceStartedAndNotAgainOnTheNextStart() =
        runTest {
            val owner = TestOwner()
            collect(owner)
            vm.accept(textIntent("payment"))
            runCurrent()
            coVerify(exactly = 0) { handleSharedPayment(any<String>()) }

            owner.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            runCurrent()
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()

            coVerify(exactly = 1) { handleSharedPayment("payment") }
        }

    @Test
    fun unfinishedShareIsRetriedByTheNextActivityAndThenConsumed() =
        runTest {
            var attempts = 0
            val release = CompletableDeferred<Unit>()
            coEvery { handleSharedPayment(image) } coAnswers {
                attempts++
                release.await()
            }
            val first = TestOwner()
            val firstCollector = collect(first)
            first.lifecycle.currentState = Lifecycle.State.STARTED
            vm.accept(imageIntent())
            runCurrent()
            assertEquals(1, attempts)

            first.lifecycle.currentState = Lifecycle.State.DESTROYED
            firstCollector.cancelAndJoin()
            val replacement = TestOwner()
            collect(replacement)
            replacement.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(2, attempts)

            release.complete(Unit)
            runCurrent()
            replacement.lifecycle.currentState = Lifecycle.State.CREATED
            runCurrent()
            replacement.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(2, attempts)
        }

    @Test
    fun newerShareReplacesOneStillInFlight() =
        runTest {
            var cancelled = false
            coEvery { handleSharedPayment(image) } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            startCollecting()
            vm.accept(imageIntent())
            runCurrent()

            vm.accept(textIntent("newer"))
            runCurrent()

            assertTrue(cancelled)
            coVerify(exactly = 1) { handleSharedPayment("newer") }
        }

    @Test
    fun onlyTheNewestShareAcceptedBeforeTheStartIsHandled() =
        runTest {
            vm.accept(textIntent("old"))
            vm.accept(textIntent("new"))
            startCollecting()
            runCurrent()

            coVerify(exactly = 0) { handleSharedPayment("old") }
            coVerify(exactly = 1) { handleSharedPayment("new") }
        }

    @Test
    fun recentsAndMalformedIntentsDoNotReplaceAPendingShare() =
        runTest {
            vm.accept(textIntent("pending"))
            vm.accept(textIntent("replay").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
            vm.accept(Intent(Intent.ACTION_SEND).setType("image/png"))
            startCollecting()
            runCurrent()

            coVerify(exactly = 1) { handleSharedPayment("pending") }
            coVerify(exactly = 0) { handleSharedPayment("replay") }
        }

    private fun TestScope.startCollecting() {
        val owner = TestOwner()
        collect(owner)
        owner.lifecycle.currentState = Lifecycle.State.STARTED
    }

    private fun TestScope.collect(owner: TestOwner) =
        backgroundScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.handlePending() }
        }

    private fun textIntent(text: String) =
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    private fun imageIntent() = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, image)

    private class TestOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.CREATED }
    }
}
