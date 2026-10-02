package co.electriccoin.zcash.ui.common.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.repeatOnLifecycle
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.usecase.HandleSharedPaymentUseCase
import co.electriccoin.zcash.ui.screen.scan.thirdparty.ThirdPartyScan
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
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
class IncomingPaymentViewModelTest {
    private val handler = mockk<HandleSharedPaymentUseCase>()
    private val router = mockk<NavigationRouter>(relaxed = true)
    private val vm = IncomingPaymentViewModel(handler, router)
    private val image = Uri.parse("content://test/payment.png")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        coEvery { handler(any<String>()) } returns Unit
        coEvery { handler(any<Uri>()) } returns Unit
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pendingDeliveryWaitsUntilStartedAndCompletedDeliveryDoesNotReplay() =
        runTest {
            val owner = TestOwner()
            collect(owner)
            vm.accept(text("payment"))
            runCurrent()
            coVerify(exactly = 0) { handler(any<String>()) }

            owner.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            runCurrent()
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            coVerify(exactly = 1) { handler("payment") }
        }

    @Test
    fun unfinishedImageRetriesAfterActivityRecreationAndIsThenConsumed() =
        runTest {
            var attempts = 0
            var completed = 0
            val release = CompletableDeferred<Unit>()
            coEvery { handler(image) } coAnswers {
                attempts++
                release.await()
                completed++
            }
            val firstOwner = TestOwner()
            val firstCollector = collect(firstOwner)
            firstOwner.lifecycle.currentState = Lifecycle.State.STARTED
            vm.accept(imageIntent())
            runCurrent()
            assertEquals(1, attempts)

            firstOwner.lifecycle.currentState = Lifecycle.State.DESTROYED
            firstCollector.cancelAndJoin()
            val replacement = TestOwner()
            collect(replacement)
            replacement.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(2, attempts)
            release.complete(Unit)
            runCurrent()
            assertEquals(1, completed)

            replacement.lifecycle.currentState = Lifecycle.State.CREATED
            runCurrent()
            replacement.lifecycle.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(2, attempts)
        }

    @Test
    fun newerViewCancelsAnImageAndKeepsThirdPartyRouting() =
        runTest {
            var cancelled = false
            coEvery { handler(image) } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            startCollecting()
            vm.accept(imageIntent())
            runCurrent()
            vm.accept(Intent(Intent.ACTION_VIEW, Uri.parse("zcash:payment")))
            runCurrent()

            assertTrue(cancelled)
            verify(exactly = 1) { router.forward(ThirdPartyScan) }
        }

    @Test
    fun identicalNewDeliveryStillReplacesUnfinishedWork() =
        runTest {
            var attempts = 0
            coEvery { handler("payment") } coAnswers {
                attempts++
                if (attempts == 1) awaitCancellation()
            }
            startCollecting()
            vm.accept(text("payment"))
            runCurrent()
            vm.accept(text("payment"))
            runCurrent()

            assertEquals(2, attempts)
        }

    @Test
    fun onlyTheNewestShareIsProcessedAfterForegrounding() =
        runTest {
            vm.accept(text("old"))
            vm.accept(text("new"))
            startCollecting()
            runCurrent()

            coVerify(exactly = 0) { handler("old") }
            coVerify(exactly = 1) { handler("new") }
        }

    @Test
    fun recentsAndMalformedIntentsDoNotReplacePendingDelivery() =
        runTest {
            vm.accept(text("pending"))
            vm.accept(text("replay").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
            vm.accept(Intent(Intent.ACTION_SEND).setType("image/png"))
            startCollecting()
            runCurrent()

            coVerify(exactly = 1) { handler("pending") }
            coVerify(exactly = 0) { handler("replay") }
        }

    private fun TestScope.startCollecting() {
        val owner = TestOwner()
        collect(owner)
        owner.lifecycle.currentState = Lifecycle.State.STARTED
    }

    private fun TestScope.collect(owner: TestOwner) =
        backgroundScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.processPending() }
        }

    private fun text(value: String) =
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value)

    private fun imageIntent() = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, image)

    private class TestOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.CREATED }
    }
}
