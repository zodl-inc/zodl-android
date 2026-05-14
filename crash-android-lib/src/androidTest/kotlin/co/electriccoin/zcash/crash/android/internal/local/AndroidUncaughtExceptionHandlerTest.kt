package co.electriccoin.zcash.crash.android.internal.local

import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class AndroidUncaughtExceptionHandlerTest {
    @Test(expected = IllegalStateException::class)
    fun requires_main_thread() {
        AndroidUncaughtExceptionHandler.register(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun cannot_initialize_twice() {
        val didFail = AtomicBoolean(true)
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            runCatching { AndroidUncaughtExceptionHandler.register(ApplicationProvider.getApplicationContext()) }
                .onFailure {
                    throw AssertionError("Failed to register once")
                }

            // Expected to fail on second registration
            try {
                AndroidUncaughtExceptionHandler.register(ApplicationProvider.getApplicationContext())
            } catch (e: IllegalStateException) {
                // Expected exception
                didFail.set(false)
                latch.countDown()
            }
        }

        latch.await()

        if (didFail.get()) {
            fail("Second initialization did not fail")
        }
    }
}
